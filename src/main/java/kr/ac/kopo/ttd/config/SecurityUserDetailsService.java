package kr.ac.kopo.ttd.config;

import kr.ac.kopo.ttd.common.crypto.HmacHasher;
import kr.ac.kopo.ttd.domain.User;
import kr.ac.kopo.ttd.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * F-11/S-01(정식 로그인)에서 세션/JWT 기반으로 교체되기 전까지 사용하는 최소 인증용
 * UserDetailsService. 이메일을 HMAC 해시로 변환해 조회하므로 이메일 원문으로 직접 검색하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SecurityUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final HmacHasher hmacHasher;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailHash(hmacHasher.hash(email))
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities("ROLE_" + user.getRole().name())
                .build();
    }
}
