package com.watchparty.watchparty.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.watchparty.watchparty.auth.jwt.JwtProvider;
import com.watchparty.watchparty.room.entity.Room;
import com.watchparty.watchparty.room.service.RoomService;
import com.watchparty.watchparty.user.entity.User;
import com.watchparty.watchparty.user.entity.UserProfile;
import com.watchparty.watchparty.user.repository.UserProfileRepository;
import com.watchparty.watchparty.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 부하테스트(k6)용 시더. `seed` 프로파일에서만 동작하며, 운영에서는 절대 로드되지 않는다.
 *
 * 실행:
 *   SPRING_PROFILES_ACTIVE=seed ./gradlew bootRun
 *   (또는 도커 이미지로 one-shot 실행 — README/노트 참고)
 *
 * 하는 일:
 *   1) 상주 호스트 유저 + 부하테스트 방 1개 생성 (호스트가 안 나가므로 방이 자동 삭제되지 않음)
 *   2) 부하 유저 N명 + 프로필 생성
 *   3) 각 유저의 액세스 토큰을 실제 JwtProvider로 발급해 tokens.json(["Bearer ...", ...])으로 저장
 *   4) ROOM_ID 로그 출력 후 애플리케이션 종료(one-shot)
 */
@Slf4j
@Component
@Profile("seed")
@RequiredArgsConstructor
public class LoadTestSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final RoomService roomService;
    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;
    private final ConfigurableApplicationContext applicationContext;

    @Value("${seed.user-count:600}")
    private int userCount;

    @Value("${seed.room-count:1}")
    private int roomCount;

    @Value("${seed.token-file:tokens.json}")
    private String tokenFile;

    @Value("${seed.room-file:rooms.json}")
    private String roomFile;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=== [SEED] 시작: 방 {}개 / 부하 유저 {}명 ===", roomCount, userCount);

        // 1) 상주 호스트 + 부하테스트 방 roomCount개 (호스트가 상주해 방 자동삭제 방지)
        //    roomCount=1이면 단일 방(락 경합), 다수면 방 분산(락 경합 완화) 테스트
        User host = getOrCreateUser("loadtest-host@watchy.test", "부하호스트");
        List<Long> roomIds = new ArrayList<>(roomCount);
        for (int i = 1; i <= roomCount; i++) {
            Room room = roomService.createRoom(host.getId(), "부하테스트방" + i, false);
            roomIds.add(room.getId());
        }

        // 2) 부하 유저 + 토큰 발급
        List<String> bearerTokens = new ArrayList<>(userCount);
        for (int i = 1; i <= userCount; i++) {
            User user = getOrCreateUser("loadtest" + i + "@watchy.test", "부하유저" + i);
            bearerTokens.add("Bearer " + jwtProvider.createAccessToken(user.getId()));
        }

        // 3) tokens.json / rooms.json 저장
        Path tokenOut = Path.of(tokenFile).toAbsolutePath();
        Path roomOut = Path.of(roomFile).toAbsolutePath();
        objectMapper.writeValue(tokenOut.toFile(), bearerTokens);
        objectMapper.writeValue(roomOut.toFile(), roomIds);

        log.info("=== [SEED] 완료 ===");
        log.info("rooms  = {}개 → {} (ID {}~{})", roomIds.size(), roomOut,
                roomIds.get(0), roomIds.get(roomIds.size() - 1));
        log.info("tokens = {}개 → {}", bearerTokens.size(), tokenOut);

        // one-shot: 시딩만 하고 종료
        int code = SpringApplication.exit(applicationContext, () -> 0);
        System.exit(code);
    }

    /** 이메일 기준 멱등 생성 — 재실행해도 중복 유저를 만들지 않는다. */
    private User getOrCreateUser(String email, String nickname) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = userRepository.save(new User(email));
            userProfileRepository.save(new UserProfile(user, nickname));
            return user;
        });
    }
}
