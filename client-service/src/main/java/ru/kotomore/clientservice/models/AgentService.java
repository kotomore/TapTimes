package ru.kotomore.clientservice.models;

import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentService {
    @Id
    private String id;
    private String name;
    private String description;
    private double price;
    private int duration;
    private String agentId;
}
