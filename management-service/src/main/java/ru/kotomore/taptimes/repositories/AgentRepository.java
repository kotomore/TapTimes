package ru.kotomore.taptimes.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import ru.kotomore.taptimes.models.Agent;

import java.util.Optional;

public interface AgentRepository extends MongoRepository<Agent, String> {
    Optional<Agent> findAgentByPhone(String phone);
}