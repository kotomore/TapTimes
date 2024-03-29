package ru.kotomore.telegramservice.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.kotomore.telegramservice.telegram.WriteReadBot;

@RestController
@RequiredArgsConstructor
public class TelegramWebhookController {
    private final WriteReadBot telegramBot;

    @PostMapping("/telegram-webhook")
    public BotApiMethod<?> onUpdateReceived(@RequestBody Update update) {
        return telegramBot.onWebhookUpdateReceived(update);
    }
}

