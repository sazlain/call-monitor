package com.monitor.call.infrastructure.mappers;

import com.monitor.call.domain.models.User;
import com.monitor.call.domain.responses.UserResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.UserEntity;

public class UserMapper {

    public static User entityToDomain(UserEntity e) {
        return User.builder()
                .id(e.getId()).name(e.getName()).email(e.getEmail())
                .password(e.getPassword()).active(e.getActive())
                .roles(e.getRoles()).mustChangePassword(e.getMustChangePassword())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt()).build();
    }

    public static UserEntity domainToEntity(User u) {
        return UserEntity.builder()
                .id(u.getId()).name(u.getName()).email(u.getEmail())
                .password(u.getPassword()).active(u.getActive())
                .roles(u.getRoles()).mustChangePassword(u.getMustChangePassword()).build();
    }

    public static UserResponse domainToResponse(User u) {
        return UserResponse.builder()
                .id(u.getId()).name(u.getName()).email(u.getEmail())
                .active(u.getActive()).roles(u.getRoles())
                .mustChangePassword(u.getMustChangePassword())
                .createdAt(u.getCreatedAt()).build();
    }
}
