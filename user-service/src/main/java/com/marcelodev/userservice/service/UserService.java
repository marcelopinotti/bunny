package com.marcelodev.userservice.service;

import com.marcelodev.userservice.domain.UserModel;
import com.marcelodev.userservice.producer.UserProducer;
import com.marcelodev.userservice.repository.UserModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserModelRepository repository;
    private final UserProducer producer;

    public UserModel save(UserModel user) {
        // gerados por requisição, nunca em campo estático (SnapStart)
        user.setId(UUID.randomUUID());
        user.setCreatedAt(Instant.now());
        UserModel saved = repository.save(user);
        producer.convertAndSend(saved);
        return saved;
    }

    public List<UserModel> findAll() {
        return repository.findAll();
    }

    public boolean deleteById(UUID id) {
        return repository.deleteById(id);
    }
}
