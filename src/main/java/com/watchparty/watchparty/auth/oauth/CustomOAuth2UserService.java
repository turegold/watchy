package com.watchparty.watchparty.auth.oauth;

import com.watchparty.watchparty.common.exception.AppException;
import com.watchparty.watchparty.common.exception.ErrorCode;
import com.watchparty.watchparty.user.entity.User;
import com.watchparty.watchparty.user.entity.UserAuth;
import com.watchparty.watchparty.user.entity.UserProfile;
import com.watchparty.watchparty.user.repository.UserAuthRepository;
import com.watchparty.watchparty.user.repository.UserProfileRepository;
import com.watchparty.watchparty.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final UserAuthRepository userAuthRepository;
    private final UserProfileRepository userProfileRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email;
        String providerId;
        String nameAttributeKey;

        if (provider.equals("google")) {
            email = (String) attributes.get("email");
            providerId = (String) attributes.get("sub");
            nameAttributeKey = "sub";
        } else if (provider.equals("kakao")) {
            providerId = String.valueOf(attributes.get("id"));
            Map<String, Object> account = (Map<String, Object>) attributes.get("kakao_account");
            email = account == null ? null : (String) account.get("email");
            nameAttributeKey = "id";
        } else {
            throw new AppException(ErrorCode.OAUTH_PROVIDER_NOT_SUPPORTED);
        }

        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.OAUTH_EMAIL_NOT_PROVIDED);
        }

        UserAuth userAuth = userAuthRepository
                .findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> registerNewUser(email, provider, providerId));

        User user = userAuth.getUser();

        Map<String, Object> modifiedAttributes = new HashMap<>(attributes);
        modifiedAttributes.put("userId", user.getId());
        modifiedAttributes.put("email", email);

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                modifiedAttributes,
                nameAttributeKey
        );
    }

    private UserAuth registerNewUser(String email, String provider, String providerId) {
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = userRepository.save(new User(email));
                    userProfileRepository.save(new UserProfile(newUser, "닉네임을 등록해주세요"));
                    return newUser;
                });

        UserAuth userAuth = new UserAuth(user, provider, providerId);
        return userAuthRepository.save(userAuth);
    }
}
