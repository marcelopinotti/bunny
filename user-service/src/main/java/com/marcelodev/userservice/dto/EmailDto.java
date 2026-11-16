package com.marcelodev.userservice.dto;


import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class EmailDto {

    private UUID id;
    private String emailTo;
    private String subject;
    private String text;

}
