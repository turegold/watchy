# Watchy 🎬

**Watchy**는 여러 사용자가 같은 영상을 **실시간으로 함께 시청하고 채팅할 수 있는 Watch Party 서비스**입니다.  
방장이 재생/일시정지/탐색을 제어하면 모든 참가자의 영상이 **동기화되어 동일한 시점으로 재생**됩니다.

> 친구들과 함께 영화를 보거나 영상을 공유하며 실시간으로 소통할 수 있는 서비스입니다.

---

# 🌐 Demo

https://watchy.site

---

# ✨ 주요 기능
<img width="800" height="600" alt="image" src="https://github.com/user-attachments/assets/725ed708-2be3-4f16-b7eb-7bb858a37509" />

### 🎥 실시간 영상 동기화

- 방장이 영상 재생을 제어
- 참가자들의 영상 플레이어가 동일한 시점으로 자동 동기화
- SEEK / PLAY / PAUSE 상태 실시간 반영

### 💬 실시간 채팅

- WebSocket 기반 채팅
- 같은 방 사용자 간 실시간 메시지 전달

### 🔐 OAuth 로그인

- 카카오 OAuth2 로그인 지원
- JWT 기반 인증

### 🏠 Watch Party 방 생성

- 방 생성 및 참여
- 방장 권한 관리
- 참가자 상태 관리

### 🔊 개인 오디오 설정

- 개인 볼륨 조절
- 음소거
- 전체 화면 모드 지원

---

# 🏗 서비스 아키텍처
```
Client
  │
  ├── watchy.site → Vercel
  │
  └── api.watchy.site → Nginx → EC2 → Docker
                                    ├ Spring Boot
                                    ├ MySQL
                                    └ Redis
```

---

# 🛠 기술 스택

## Frontend

- React
- Vite
- STOMP.js
- SockJS
- Axios

## Backend

- Java 17
- Spring Boot
- Spring Security
- OAuth2 Client
- JWT Authentication
- WebSocket (STOMP)

## Database

- MySQL
- Redis

## Infrastructure

- AWS EC2
- Docker
- Docker Compose
- Nginx (Reverse Proxy)
- Let's Encrypt (HTTPS)

---
