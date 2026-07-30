package com.example.file_management.controller;

import com.example.file_management.controller.dto.AddUserDTO;
import com.example.file_management.entity.User;
import com.example.file_management.exception.UserNotExistException;
import com.example.file_management.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private static final Logger logger =
            LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/add")
    public DataVO<Void> addUser(@RequestBody AddUserDTO addUserDTO) {
        long start = System.currentTimeMillis();
        try {
            validateAddUser(addUserDTO);
            userService.addUser(addUserDTO);
            return success(start, null);
        } catch (Exception exception) {
            return failure(start, exception, "add user");
        }
    }

    @GetMapping("/query")
    public DataVO<User> queryById(@RequestParam int id) {
        long start = System.currentTimeMillis();
        try {
            requirePositive(id, "id");
            return success(start, requireUser(userService.queryById(id)));
        } catch (Exception exception) {
            return failure(start, exception, "query user by id");
        }
    }

    @GetMapping("/email")
    public DataVO<User> queryByEmail(@RequestParam String email) {
        long start = System.currentTimeMillis();
        try {
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("email must not be blank");
            }
            return success(
                    start,
                    requireUser(userService.queryByEmail(email.trim())));
        } catch (Exception exception) {
            return failure(start, exception, "query user by email");
        }
    }

    @GetMapping("/list")
    public DataVO<List<User>> queryByTenantId(@RequestParam int tenantId) {
        long start = System.currentTimeMillis();
        try {
            requirePositive(tenantId, "tenantId");
            return success(start, userService.queryByTenantId(tenantId));
        } catch (Exception exception) {
            return failure(start, exception, "query users by tenant");
        }
    }

    @GetMapping("/group")
    public DataVO<List<User>> queryByGroupId(@RequestParam int groupId) {
        long start = System.currentTimeMillis();
        try {
            requirePositive(groupId, "groupId");
            return success(start, userService.queryByGroupId(groupId));
        } catch (Exception exception) {
            return failure(start, exception, "query users by group");
        }
    }

    private void validateAddUser(AddUserDTO addUserDTO) {
        if (addUserDTO == null) {
            throw new IllegalArgumentException("user payload is required");
        }
        requirePositive(addUserDTO.tenantId(), "tenantId");
        requirePositive(addUserDTO.groupId(), "groupId");
        if (addUserDTO.name() == null || addUserDTO.name().isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (addUserDTO.email() == null || addUserDTO.email().isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
    }

    private void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private User requireUser(User user) {
        if (user == null) {
            throw new UserNotExistException("user does not exist");
        }
        return user;
    }

    private <T> DataVO<T> success(long start, T data) {
        return DataVO.buildDataVO(
                200,
                System.currentTimeMillis() - start,
                true,
                null,
                data);
    }

    private <T> DataVO<T> failure(
            long start,
            Exception exception,
            String operation) {
        long elapsed = System.currentTimeMillis() - start;
        if (exception instanceof IllegalArgumentException) {
            logger.warn("failed to {}: {}", operation, exception.getMessage());
            return DataVO.buildDataVO(
                    400, elapsed, false, exception.getMessage(), null);
        }
        if (exception instanceof UserNotExistException) {
            logger.warn("failed to {}: {}", operation, exception.getMessage());
            return DataVO.buildDataVO(
                    404, elapsed, false, exception.getMessage(), null);
        }
        logger.error("failed to " + operation, exception);
        return DataVO.buildDataVO(
                500, elapsed, false, "other unknown error", null);
    }
}
