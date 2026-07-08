package kr.ac.kopo.ttd.service;

import kr.ac.kopo.ttd.common.exception.InvalidStatusTransitionException;
import kr.ac.kopo.ttd.common.exception.ProblemNotFoundException;
import kr.ac.kopo.ttd.common.exception.SkeletonCodeMismatchException;
import kr.ac.kopo.ttd.domain.Problem;
import kr.ac.kopo.ttd.domain.ProblemStatus;
import kr.ac.kopo.ttd.domain.ProblemType;
import kr.ac.kopo.ttd.dto.AdminProblemResponse;
import kr.ac.kopo.ttd.dto.ProblemCreateRequest;
import kr.ac.kopo.ttd.dto.ProblemResponse;
import kr.ac.kopo.ttd.dto.ProblemStatusUpdateRequest;
import kr.ac.kopo.ttd.dto.ProblemSummaryResponse;
import kr.ac.kopo.ttd.dto.ProblemUpdateRequest;
import kr.ac.kopo.ttd.repository.ProblemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemService {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final ProblemRepository problemRepository;

    // 관리자: 생성/조회/수정/삭제

    @Transactional
    public AdminProblemResponse createProblem(ProblemCreateRequest request) {
        validateSkeletonConsistency(request.type(), request.skeletonCode());

        Problem problem = Problem.builder()
                .title(request.title())
                .difficulty(request.difficulty())
                .type(request.type())
                .sourceType(request.sourceType())
                .description(request.description())
                .requirements(request.requirements())
                .constraints(request.constraints())
                .skeletonCode(request.skeletonCode())
                .maxAttempts(request.maxAttempts() == null ? DEFAULT_MAX_ATTEMPTS : request.maxAttempts())
                .build();

        return AdminProblemResponse.from(problemRepository.save(problem));
    }

    public List<AdminProblemResponse> getAllProblems() {
        return problemRepository.findAll().stream()
                .map(AdminProblemResponse::from)
                .toList();
    }

    public AdminProblemResponse getProblem(Long id) {
        return AdminProblemResponse.from(findProblemOrThrow(id));
    }

    @Transactional
    public AdminProblemResponse updateProblem(Long id, ProblemUpdateRequest request) {
        validateSkeletonConsistency(request.type(), request.skeletonCode());

        Problem problem = findProblemOrThrow(id);
        problem.updateContent(
                request.title(),
                request.difficulty(),
                request.type(),
                request.sourceType(),
                request.description(),
                request.requirements(),
                request.constraints(),
                request.skeletonCode(),
                request.maxAttempts()
        );
        return AdminProblemResponse.from(problem);
    }

    @Transactional
    public void deleteProblem(Long id) {
        if (!problemRepository.existsById(id)) {
            throw new ProblemNotFoundException();
        }
        problemRepository.deleteById(id);
    }

    @Transactional
    public AdminProblemResponse changeStatus(Long id, ProblemStatusUpdateRequest request) {
        Problem problem = findProblemOrThrow(id);
        ProblemStatus target = request.status();
        if (!problem.getStatus().canTransitionTo(target)) {
            throw new InvalidStatusTransitionException();
        }
        problem.changeStatus(target);
        return AdminProblemResponse.from(problem);
    }

    // 응시자: active 문제만 노출

    public List<ProblemSummaryResponse> getActiveProblems() {
        return problemRepository.findAllByStatus(ProblemStatus.ACTIVE).stream()
                .map(ProblemSummaryResponse::from)
                .toList();
    }

    public ProblemResponse getActiveProblem(Long id) {
        Problem problem = problemRepository.findByIdAndStatus(id, ProblemStatus.ACTIVE)
                .orElseThrow(ProblemNotFoundException::new);
        return ProblemResponse.from(problem);
    }

    private Problem findProblemOrThrow(Long id) {
        return problemRepository.findById(id).orElseThrow(ProblemNotFoundException::new);
    }

    /**
     * 스켈레톤형 문제(SKELETON_*)는 skeleton_code가 필수이며, 그 외 유형은 skeleton_code를 가질 수 없다.
     */
    private void validateSkeletonConsistency(ProblemType type, String skeletonCode) {
        boolean hasSkeletonCode = skeletonCode != null && !skeletonCode.isBlank();
        if (type.isSkeleton() != hasSkeletonCode) {
            throw new SkeletonCodeMismatchException();
        }
    }
}
