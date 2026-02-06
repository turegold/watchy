package com.watchparty.watchparty.room.entity;

import com.watchparty.watchparty.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "rooms")
@Getter
@NoArgsConstructor(access =  AccessLevel.PROTECTED)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String title;

    @ManyToOne(fetch =  FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @Column(nullable = false)
    private boolean isPrivate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Room(String title, User host, boolean isPrivate) {
        this.title = title;
        this.host = host;
        this.isPrivate = isPrivate;
        this.createdAt = LocalDateTime.now();
    }

    // 방장 변경
    public void changeHost(User newHost){
        if(newHost == null){
            throw new IllegalArgumentException("방장은 null일 수 없습니다.");
        }
        this.host = newHost;
    }
}
