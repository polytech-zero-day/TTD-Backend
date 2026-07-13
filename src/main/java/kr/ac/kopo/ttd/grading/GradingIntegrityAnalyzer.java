package kr.ac.kopo.ttd.grading;

import kr.ac.kopo.ttd.domain.AttemptMessage;
import kr.ac.kopo.ttd.domain.MessageRole;
import kr.ac.kopo.ttd.domain.Problem;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * LLM 채점과 독립적으로 응시 대화의 저관여·채점 조작 신호를 탐지한다.
 * 탐지 결과는 점수 정책의 상한과 결과 화면 신뢰도 안내에 함께 사용한다.
 */
public final class GradingIntegrityAnalyzer {

    public static final String FLAG_LOW_ENGAGEMENT_COPY = "LOW_ENGAGEMENT_COPY";
    public static final String FLAG_POSSIBLE_GRADING_INJECTION = "POSSIBLE_GRADING_INJECTION";
    public static final String FLAG_NO_AI_INTERACTION = "NO_AI_INTERACTION";

    private static final int DEFAULT_PROCESS_SCORE_CAP = 30;
    private static final int LOW_ENGAGEMENT_PROCESS_SCORE_CAP = 10;
    private static final int INJECTION_PROCESS_SCORE_CAP = 0;

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?is)(ignore|disregard).{0,40}(previous|above|system|instruction)"),
            Pattern.compile("(?is)(이전|위|시스템).{0,30}(지시|명령|프롬프트).{0,20}(무시|따르지)"),
            Pattern.compile("(?is)(system|developer).{0,30}(prompt|message|instruction)"),
            Pattern.compile("(?is)(score|점수|채점).{0,40}(100|maximum|최대|무조건|ignore|무시|변경|조작)"),
            Pattern.compile("(?is)(100|maximum|만점|최대).{0,30}(score|점|점수|채점)"),
            Pattern.compile("(?is)(role|역할).{0,30}(change|변경|전환)"),
            Pattern.compile("(?is)(you are|당신은).{0,30}(grader|judge|채점관|심사관)"),
            Pattern.compile("(?is)\\[/?data[^]]*]")
    );
    private static final Pattern BASE64_BLOCK = Pattern.compile("(?<![A-Za-z0-9+/])[A-Za-z0-9+/]{20,}={0,2}(?![A-Za-z0-9+/])");

    private static final List<String> VERIFICATION_PHRASES = List.of(
            "스스로 검증", "자체 검증", "테스트 케이스로 확인", "누락 여부를 확인",
            "엣지 케이스", "에지 케이스", "반례를 확인", "실행 결과를 확인",
            "답변 후 검증", "self-check", "edge case", "verify the result"
    );

    private GradingIntegrityAnalyzer() {
    }

    public static Assessment assess(Problem problem, String artifact, List<AttemptMessage> history) {
        List<String> userMessages = history == null ? List.of() : history.stream()
                .filter(message -> message.getRole() == MessageRole.USER)
                .map(AttemptMessage::getContent)
                .filter(content -> content != null && !content.isBlank())
                .toList();
        List<String> conversationContents = history == null ? List.of() : history.stream()
                .map(AttemptMessage::getContent)
                .filter(content -> content != null && !content.isBlank())
                .toList();

        List<String> flags = new ArrayList<>();
        boolean noAiInteraction = userMessages.isEmpty();
        if (noAiInteraction) {
            flags.add(FLAG_NO_AI_INTERACTION);
        }
        String allSubmittedText = String.join("\n", conversationContents)
                + "\n" + (artifact == null ? "" : artifact);
        boolean injectionSignal = containsInjectionSignal(allSubmittedText);
        if (injectionSignal) {
            flags.add(FLAG_POSSIBLE_GRADING_INJECTION);
        }

        boolean lowEngagementCopy = isLowEngagementCopy(problem, userMessages);
        if (lowEngagementCopy) {
            flags.add(FLAG_LOW_ENGAGEMENT_COPY);
        }

        String confidence = injectionSignal
                ? "LOW"
                : (lowEngagementCopy || noAiInteraction ? "MEDIUM" : "HIGH");
        int processScoreCap = injectionSignal
                ? INJECTION_PROCESS_SCORE_CAP
                : (noAiInteraction ? 0
                    : (lowEngagementCopy ? LOW_ENGAGEMENT_PROCESS_SCORE_CAP : DEFAULT_PROCESS_SCORE_CAP));
        return new Assessment(processScoreCap, confidence, flags);
    }

    private static boolean isLowEngagementCopy(Problem problem, List<String> userMessages) {
        if (problem == null || userMessages.size() != 1) {
            return false;
        }
        String problemText = String.join(" ", List.of(
                nullToEmpty(problem.getTitle()),
                nullToEmpty(problem.getDescription()),
                String.join(" ", problem.getRequirements() == null ? List.of() : problem.getRequirements()),
                String.join(" ", problem.getConstraints() == null ? List.of() : problem.getConstraints())
        ));
        String firstPrompt = userMessages.get(0);
        return isNearVerbatimCopy(problemText, firstPrompt)
                && !hasExplicitVerificationInstruction(problemText, firstPrompt);
    }

    private static boolean isNearVerbatimCopy(String problemText, String prompt) {
        String normalizedProblem = normalize(problemText);
        String normalizedPrompt = normalize(prompt);
        if (normalizedProblem.length() < 30 || normalizedPrompt.length() < 30) {
            return false;
        }

        Set<String> problemShingles = shingles(normalizedProblem, 3);
        Set<String> promptShingles = shingles(normalizedPrompt, 3);
        if (problemShingles.isEmpty() || promptShingles.isEmpty()) {
            return false;
        }

        long intersection = problemShingles.stream().filter(promptShingles::contains).count();
        double problemCoverage = (double) intersection / problemShingles.size();
        double promptNovelty = 1.0 - (double) intersection / promptShingles.size();
        return problemCoverage >= 0.65 && promptNovelty <= 0.35;
    }

    private static boolean hasExplicitVerificationInstruction(String problemText, String prompt) {
        String lowerProblem = problemText.toLowerCase(Locale.ROOT);
        String lowerPrompt = prompt.toLowerCase(Locale.ROOT);
        return VERIFICATION_PHRASES.stream()
                .anyMatch(phrase -> lowerPrompt.contains(phrase) && !lowerProblem.contains(phrase));
    }

    private static boolean containsInjectionSignal(String content) {
        if (content == null) {
            return false;
        }
        String cleaned = content.replaceAll("[\u200B-\u200D\u2060\uFEFF]", "");
        String compact = cleaned.replaceAll("\\s+", "");
        if (matchesInjectionPattern(cleaned) || matchesInjectionPattern(compact)) {
            return true;
        }
        var matcher = BASE64_BLOCK.matcher(cleaned);
        while (matcher.find()) {
            try {
                String decoded = new String(Base64.getDecoder().decode(matcher.group()), StandardCharsets.UTF_8);
                if (matchesInjectionPattern(decoded)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // Base64처럼 보이는 일반 텍스트는 탐지 대상에서 제외한다.
            }
        }
        return false;
    }

    private static boolean matchesInjectionPattern(String content) {
        return INJECTION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(content).find());
    }

    private static Set<String> shingles(String value, int size) {
        Set<String> result = new HashSet<>();
        for (int i = 0; i <= value.length() - size; i++) {
            result.add(value.substring(i, i + size));
        }
        return result;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-z가-힣]", "");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record Assessment(int processScoreCap, String confidence, List<String> flags) {
        public Assessment {
            flags = List.copyOf(flags);
        }
    }
}
