package com.marcelodev.userservice.controller;


import com.marcelodev.userservice.domain.UserModel;
import com.marcelodev.userservice.dto.CreateUserDto;
import com.marcelodev.userservice.dto.UserResponseDto;
import com.marcelodev.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;


    @PostMapping
    public ResponseEntity<UserResponseDto> addUser(@Valid @RequestBody CreateUserDto dto) {
        UserModel userModel = new UserModel();
        userModel.setName(dto.name());
        userModel.setEmail(dto.email());
        UserModel savedUser = userService.save(userModel);
        return ResponseEntity.status(201).body(UserResponseDto.from(savedUser));
    }
}
