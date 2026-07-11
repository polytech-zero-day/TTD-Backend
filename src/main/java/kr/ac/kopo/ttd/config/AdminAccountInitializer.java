package kr.ac.kopo.ttd.config;

import kr.ac.kopo.ttd.domain.UserRole;
import kr.ac.kopo.ttd.dto.UserCreateRequest;
import kr.ac.kopo.ttd.repository.UserRepository;
import kr.ac.kopo.ttd.service.UserAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Admin만 User CRUD를 할 수 있게 막아두면 최초 Admin이 없는 닭-달걀 문제가 생기므로,
 * 앱 기동 시 Admin이 하나도 없으면 시드 계정을 생성한다. H2는 인메모리라 재기동마다
 * 비어있어 매번 실행되지만, existsByRole 확인으로 중복 생성은 막는다(멱등).
 */
@Slf4j
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    /** 노출된 기본 비밀번호. 운영에서 이대로 뜨면 즉시 탈취 대상이므로 기동 시 경고한다. */
    private static final String DEFAULT_ADMIN_PASSWORD = "admin1234!";

    private final UserRepository userRepository;
    private final UserAdminService userAdminService;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminNickname;

    public AdminAccountInitializer(
            UserRepository userRepository,
            UserAdminService userAdminService,
            @Value("${app.admin-init.email}") String adminEmail,
            @Value("${app.admin-init.password}") String adminPassword,
            @Value("${app.admin-init.nickname}") String adminNickname) {
        this.userRepository = userRepository;
        this.userAdminService = userAdminService;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminNickname = adminNickname;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            return;
        }
        if (DEFAULT_ADMIN_PASSWORD.equals(adminPassword)) {
            log.warn("""
                    ⚠️ 관리자 시드 계정이 기본 비밀번호로 생성됩니다. 운영 배포 시 반드시
                    ADMIN_INIT_PASSWORD 환경변수로 강한 비밀번호를 지정하세요 (email={}).""", adminEmail);
        }
        userAdminService.createUser(new UserCreateRequest(adminEmail, adminPassword, adminNickname, UserRole.ADMIN));
    }
}
