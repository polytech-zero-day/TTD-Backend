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
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("관리자 계정이 없지만 ADMIN_INIT_PASSWORD가 설정되지 않아 시드 생성을 건너뜁니다.");
            return;
        }
        userAdminService.createUser(new UserCreateRequest(adminEmail, adminPassword, adminNickname, UserRole.ADMIN));
    }
}
