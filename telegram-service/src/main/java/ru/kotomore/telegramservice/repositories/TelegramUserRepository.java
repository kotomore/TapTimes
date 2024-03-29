package ru.kotomore.telegramservice.repositories;

import ru.kotomore.telegramservice.models.TelegramUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelegramUserRepository extends JpaRepository<TelegramUser, Integer> {
    Optional<TelegramUser> findByPhone(String phone);
    Optional<TelegramUser> findByAgentId(String agentId);
    Optional<TelegramUser> findByChatId(String chatId);

}
