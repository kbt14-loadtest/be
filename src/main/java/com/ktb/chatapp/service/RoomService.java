package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.*;
import com.ktb.chatapp.event.RoomCreatedEvent;
import com.ktb.chatapp.event.RoomUpdatedEvent;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.ktb.chatapp.util.image.ImageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final ImageUtils imageUtils;

    public RoomsResponse getAllRoomsWithPagination(
            com.ktb.chatapp.dto.PageRequest pageRequest, String name) {

        try {
            // 정렬 설정 검증
            if (!pageRequest.isValidSortField()) {
                pageRequest.setSortField("createdAt");
            }
            if (!pageRequest.isValidSortOrder()) {
                pageRequest.setSortOrder("desc");
            }

            // 정렬 방향 설정
            Sort.Direction direction = "desc".equals(pageRequest.getSortOrder())
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;

            // 정렬 필드 매핑 (participantsCount는 특별 처리 필요)
            String sortField = pageRequest.getSortField();
            if ("participantsCount".equals(sortField)) {
                sortField = "participantIds"; // MongoDB 필드명으로 변경
            }

            // Pageable 객체 생성
            PageRequest springPageRequest = PageRequest.of(
                    pageRequest.getPage(),
                    pageRequest.getPageSize(),
                    Sort.by(direction, sortField)
            );

            // 검색어가 있는 경우와 없는 경우 분리
            Page<Room> roomPage;
            if (pageRequest.getSearch() != null && !pageRequest.getSearch().trim().isEmpty()) {
                roomPage = roomRepository.findByNameContainingIgnoreCase(
                        pageRequest.getSearch().trim(), springPageRequest);
            } else {
                roomPage = roomRepository.findAll(springPageRequest);
            }

            // Room을 RoomResponse로 변환
            List<RoomResponse> roomResponses = roomPage.getContent().stream()
                    .map(room -> mapToRoomResponse(room, name))
                    .collect(Collectors.toList());

            // 메타데이터 생성
            PageMetadata metadata = PageMetadata.builder()
                    .total(roomPage.getTotalElements())
                    .page(pageRequest.getPage())
                    .pageSize(pageRequest.getPageSize())
                    .totalPages(roomPage.getTotalPages())
                    .hasMore(roomPage.hasNext())
                    .currentCount(roomResponses.size())
                    .sort(PageMetadata.SortInfo.builder()
                            .field(pageRequest.getSortField())
                            .order(pageRequest.getSortOrder())
                            .build())
                    .build();

            return RoomsResponse.builder()
                    .success(true)
                    .data(roomResponses)
                    .metadata(metadata)
                    .build();

        } catch (Exception e) {
            log.error("방 목록 조회 에러", e);
            return RoomsResponse.builder()
                    .success(false)
                    .data(List.of())
                    .build();
        }
    }

    public HealthResponse getHealthStatus() {
        try {
            long startTime = System.currentTimeMillis();

            // MongoDB 연결 상태 확인
            boolean isMongoConnected = false;
            long latency = 0;

            try {
                // 간단한 쿼리로 연결 상태 및 지연 시간 측정
                roomRepository.findOneForHealthCheck();
                long endTime = System.currentTimeMillis();
                latency = endTime - startTime;
                isMongoConnected = true;
            } catch (Exception e) {
                log.warn("MongoDB 연결 확인 실패", e);
                isMongoConnected = false;
            }

            // 최근 활동 조회
            LocalDateTime lastActivity = roomRepository.findMostRecentRoom()
                    .map(Room::getCreatedAt)
                    .orElse(null);

            // 서비스 상태 정보 구성
            Map<String, HealthResponse.ServiceHealth> services = new HashMap<>();
            services.put("database", HealthResponse.ServiceHealth.builder()
                    .connected(isMongoConnected)
                    .latency(latency)
                    .build());

            return HealthResponse.builder()
                    .success(true)
                    .services(services)
                    .lastActivity(lastActivity)
                    .build();

        } catch (Exception e) {
            log.error("Health check 실행 중 에러 발생", e);
            return HealthResponse.builder()
                    .success(false)
                    .services(new HashMap<>())
                    .build();
        }
    }

    public Room createRoom(CreateRoomRequest createRoomRequest, String name) {
        User creator = userRepository.findByEmail(name)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + name));

        Room room = new Room();
        room.setName(createRoomRequest.getName().trim());
        room.setCreator(creator.getId());
        room.getParticipantIds().add(creator.getId());

        if (createRoomRequest.getPassword() != null && !createRoomRequest.getPassword().isEmpty()) {
            room.setHasPassword(true);
            room.setPassword(passwordEncoder.encode(createRoomRequest.getPassword()));
        }

        Room savedRoom = roomRepository.save(room);

        // Publish event for room created
        try {
            RoomResponse roomResponse = mapToRoomResponse(savedRoom, name);
            eventPublisher.publishEvent(new RoomCreatedEvent(this, roomResponse));
        } catch (Exception e) {
            log.error("roomCreated 이벤트 발행 실패", e);
        }

        return savedRoom;
    }

    public Optional<Room> findRoomById(String roomId) {
        return roomRepository.findById(roomId);
    }

    public Room joinRoom(String roomId, String password, String name) {
        Optional<Room> roomOpt = roomRepository.findById(roomId);
        if (roomOpt.isEmpty()) {
            return null;
        }

        Room room = roomOpt.get();
        User user = userRepository.findByEmail(name)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + name));

        // 비밀번호 확인
        if (room.isHasPassword()) {
            if (password == null || !passwordEncoder.matches(password, room.getPassword())) {
                throw new RuntimeException("비밀번호가 일치하지 않습니다.");
            }
        }

        // 이미 참여중인지 확인
        if (!room.getParticipantIds().contains(user.getId())) {
            // 채팅방 참여
            room.getParticipantIds().add(user.getId());
            room = roomRepository.save(room);
        }

        try {
            RoomResponse roomResponse = mapToRoomResponse(room, name);
            eventPublisher.publishEvent(new RoomUpdatedEvent(this, roomId, roomResponse));
        } catch (Exception e) {
            log.error("roomUpdate 이벤트 발행 실패", e);
        }

        return room;
    }

    private RoomResponse mapToRoomResponse(Room room, String userId) {
        if (room == null) return null;

        // Creator 조회 (한 번의 조회)
        User creator = null;
        if (room.getCreator() != null) {
            creator = userRepository.findById(room.getCreator()).orElse(null);
        }

        // Participants 조회 (Batch 조회로 N+1 해결)
        Set<String> participantIds = room.getParticipantIds();
        List<User> participants = participantIds != null && !participantIds.isEmpty()
                ? userRepository.findAllById(participantIds)
                : List.of();

        // 최근 메시지 수
        LocalDateTime tenMinutesAgo = LocalDateTime.now().minusMinutes(10);
        long recentMessageCount = messageRepository.countRecentMessagesByRoomId(room.getId(), tenMinutesAgo);

        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName() != null ? room.getName() : "제목 없음")
                .hasPassword(room.isHasPassword())

                // Creator
                .creator(creator != null ? UserResponse.from(creator) : null)

                // Participants
                .participants(
                        participants.stream()
                                .filter(p -> p != null && p.getId() != null)
                                .map(p -> UserResponse.builder()
                                        .id(p.getId())
                                        .name(p.getName() != null ? p.getName() : "알 수 없음")
                                        .email(p.getEmail() != null ? p.getEmail() : "")
                                        .presignedProfileImage(
                                                imageUtils.generatePresignedUrlWithKey(p.getProfileImageKey(), Duration.ofHours(1))
                                        )
                                        .build()
                                )
                                .toList()
                )

                // Additional Info
                .createdAtDateTime(room.getCreatedAt())
                .isCreator(creator != null && creator.getId().equals(userId))
                .recentMessageCount((int) recentMessageCount)
                .build();
    }

}