package ru.set404.clients.models;

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
}
