package kr.ac.kopo.ttd.config;

import kr.ac.kopo.ttd.domain.UserRole;
import kr.ac.kopo.ttd.repository.UserRepository;
import kr.ac.kopo.ttd.service.UserAdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAdminService userAdminService;

    @Test
    void Admin이_이미_있으면_생성하지_않는다() {
        given(userRepository.existsByRole(UserRole.ADMIN)).willReturn(true);
        AdminAccountInitializer initializer = new AdminAccountInitializer(
                userRepository, userAdminService, "admin@ttd.local", "admin1234!", "admin");

        initializer.run(null);

        verify(userAdminService, never()).createUser(any());
    }

    @Test
    void Admin이_없으면_시드_계정을_생성한다() {
        given(userRepository.existsByRole(UserRole.ADMIN)).willReturn(false);
        AdminAccountInitializer initializer = new AdminAccountInitializer(
                userRepository, userAdminService, "admin@ttd.local", "admin1234!", "admin");

        initializer.run(null);

        verify(userAdminService).createUser(any());
    }
}
