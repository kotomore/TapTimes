package ru.set404.telegramservice.telegram.handlers;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.set404.telegramservice.dto.telegram.AgentMSG;
import ru.set404.telegramservice.dto.telegram.AgentServiceMSG;
import ru.set404.telegramservice.dto.telegram.ScheduleMSG;
import ru.set404.telegramservice.dto.telegram.TelegramMessage;
import ru.set404.telegramservice.enums.CallbackActionDefinitionEnum;
import ru.set404.telegramservice.enums.CallbackActionPartsEnum;
import ru.set404.telegramservice.models.TelegramUser;
import ru.set404.telegramservice.repositories.TelegramUserRepository;
import ru.set404.telegramservice.services.RabbitService;
import ru.set404.telegramservice.services.WaitAnswerService;
import ru.set404.telegramservice.telegram.keyboards.InlineKeyboardMaker;
import ru.set404.telegramservice.telegram.keyboards.ReplyKeyboardMaker;
import ru.set404.telegramservice.telegram.util.NeedAnswer;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Component
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class TelegramMessageHandler {

    InlineKeyboardMaker inlineKeyboardMaker;
    RabbitService rabbitService;
    TelegramUserRepository repository;
    WaitAnswerService waitAnswerService;

    public BotApiMethod<?> answerMessage(Message message) throws InterruptedException {
        String chatId = message.getChatId().toString();
        String inputText = message.getText();

        switch (inputText) {
            case "/start":
                return getRegMessage(chatId);
            case "Редактирование услуг":
                Optional<TelegramUser> user = repository.findByChatId(chatId);
                user.ifPresent(telegramUser -> rabbitService.sendTelegramMessage(telegramUser.getAgentId(), TelegramMessage.Action.SERVICE_INFO));
                return null;
            case "Личные данные":
                user = repository.findByChatId(chatId);
                user.ifPresent(telegramUser -> rabbitService.sendTelegramMessage(telegramUser.getAgentId(), TelegramMessage.Action.AGENT_INFO));
                return null;
            case "Расписание":
                user = repository.findByChatId(chatId);
                user.ifPresent(telegramUser -> rabbitService.sendTelegramMessage(telegramUser.getAgentId(), TelegramMessage.Action.SCHEDULES));
                return null;
            case "Записи":
                user = repository.findByChatId(chatId);
                user.ifPresent(telegramUser -> rabbitService.sendTelegramMessage(telegramUser.getAgentId(), TelegramMessage.Action.APPOINTMENTS));
                return null;
            case "test":
                return sendScheduleMessage(chatId);
        }

        if (waitAnswerService.contains(chatId)) {
            NeedAnswer needAnswer = waitAnswerService.getWaiter(chatId);
            CallbackActionPartsEnum actionPart = needAnswer.actionPart();
            CallbackActionDefinitionEnum definition = needAnswer.definition();

            if (actionPart == CallbackActionPartsEnum.SERVICE_) {
                if (definition == CallbackActionDefinitionEnum.NAME) {
                    return updateAgentServiceName(message, chatId);
                } else if (definition == CallbackActionDefinitionEnum.DESCRIPTION) {
                    return updateAgentServiceDescription(message, chatId);
                } else if (definition == CallbackActionDefinitionEnum.DURATION) {
                    return updateAgentServiceDuration(message, chatId);
                } else if (definition == CallbackActionDefinitionEnum.PRICE) {
                    return updateAgentServicePrice(message, chatId);
                }
            }

            if (actionPart == CallbackActionPartsEnum.AGENT_) {
                if (definition == CallbackActionDefinitionEnum.NAME) {
                    return updateAgentName(message, chatId);
                } else if (definition == CallbackActionDefinitionEnum.PASSWORD) {
                    return updateAgentPassword(message, chatId);
                }
            }

            if (actionPart == CallbackActionPartsEnum.SCHEDULE_) {
                return updateAgentSchedule(message, chatId);
            }
        }
        return null;
    }

    private BotApiMethod<?> updateAgentServiceName(Message message, String chatId) throws InterruptedException {
        AgentServiceMSG service = new AgentServiceMSG();
        service.setName(message.getText());
        return updateService(chatId, service);
    }

    private BotApiMethod<?> updateAgentServiceDescription(Message message, String chatId) throws InterruptedException {
        AgentServiceMSG service = new AgentServiceMSG();
        service.setDescription(message.getText());
        return updateService(chatId, service);
    }

    private BotApiMethod<?> updateAgentServiceDuration(Message message, String chatId) throws InterruptedException {
        AgentServiceMSG service = new AgentServiceMSG();
        try {
            int duration = Integer.parseInt(message.getText());
            service.setDuration(duration);
            return updateService(chatId, service);
        } catch (NumberFormatException exception) {
            return new SendMessage(chatId, "Введите длительность услуги");
        }
    }

    private BotApiMethod<?> updateAgentServicePrice(Message message, String chatId) throws InterruptedException {
        AgentServiceMSG service = new AgentServiceMSG();
        try {
            double price = Double.parseDouble(message.getText());
            service.setPrice(price);
            return updateService(chatId, service);
        } catch (NumberFormatException exception) {
            return new SendMessage(chatId, "Введите стоимость услуги");
        }
    }

    private BotApiMethod<?> updateService(String chatId, AgentServiceMSG service) throws InterruptedException {
        Optional<TelegramUser> user = repository.findByChatId(chatId);
        if (user.isPresent()) {
            service.setAgentId(user.get().getAgentId());
            rabbitService.updateService(service);
            waitAnswerService.removeFromWaitingList(chatId);
            Thread.sleep(1000);
            rabbitService.sendTelegramMessage(user.get().getAgentId(), TelegramMessage.Action.SERVICE_INFO);
        }
        return null;
    }

    private BotApiMethod<?> updateAgentName(Message message, String chatId) throws InterruptedException {
        AgentMSG agentMSG = new AgentMSG();
        agentMSG.setName(message.getText());
        return updateAgent(chatId, agentMSG);
    }

    private BotApiMethod<?> updateAgentPassword(Message message, String chatId) throws InterruptedException {
        AgentMSG agentMSG = new AgentMSG();
        agentMSG.setPassword(message.getText());
        return updateAgent(chatId, agentMSG);
    }

    private BotApiMethod<?> updateAgent(String chatId, AgentMSG agentMSG) throws InterruptedException {
        Optional<TelegramUser> user = repository.findByChatId(chatId);
        if (user.isPresent()) {
            agentMSG.setId(user.get().getAgentId());
            rabbitService.updateAgent(agentMSG);
            waitAnswerService.removeFromWaitingList(chatId);
            Thread.sleep(1000);
            rabbitService.sendTelegramMessage(user.get().getAgentId(), TelegramMessage.Action.AGENT_INFO);
        }
        return null;
    }

    private BotApiMethod<?> updateAgentSchedule(Message message, String chatId) throws InterruptedException {
        ScheduleMSG scheduleMSG = new ScheduleMSG();

        String[] messages = message.getText().split("\n");
        if (messages.length == 2) {
            try {
                for (int i = 0; i < messages.length; i++) {
                    scheduleMSG.setAgentId(message.getText());
                    LocalDateTime dateTime = LocalDateTime.parse(messages[i].replace(" ", "T"));

                    if (i == 0) {
                        scheduleMSG.setDateStart(dateTime.toLocalDate().toString());
                        scheduleMSG.setTimeStart(dateTime.toLocalTime().toString());
                    } else {
                        scheduleMSG.setDateEnd(dateTime.toLocalDate().toString());
                        scheduleMSG.setTimeEnd(dateTime.toLocalTime().toString());
                    }
                }
                return updateSchedule(chatId, scheduleMSG);

            } catch (DateTimeParseException ex) {
                return new SendMessage(chatId, "Введите дату и время в указанном формате");
            }
        }
        return new SendMessage(chatId, "Введите дату и время в указанном формате");
    }

    private BotApiMethod<?> updateSchedule(String chatId, ScheduleMSG schedule) throws InterruptedException {
        Optional<TelegramUser> user = repository.findByChatId(chatId);
        if (user.isPresent()) {
            schedule.setAgentId(user.get().getAgentId());
            rabbitService.updateSchedule(schedule);
            waitAnswerService.removeFromWaitingList(chatId);
            Thread.sleep(1000);
            rabbitService.sendTelegramMessage(user.get().getAgentId(), TelegramMessage.Action.SCHEDULES);
        }
        return null;
    }

    public void processRegistration(Message message) {
        String chatId = message.getChatId().toString();
        String phone = message.getContact().getPhoneNumber();

        TelegramUser user = repository.findByPhone(phone).orElseGet(TelegramUser::new);
        user.setPhone(phone);
        user.setChatId(chatId);
        repository.save(user);
        rabbitService.registerAgentByPhone(phone);
    }


    private SendMessage getRegMessage(String chatId) {
        SendMessage sendMessage = new SendMessage(chatId, "Нажмите кнопку регистрация");
        sendMessage.enableMarkdown(true);
        ReplyKeyboardMaker maker = new ReplyKeyboardMaker();

        sendMessage.setReplyMarkup(maker.getSingleButtonKeyboard("Регистрация"));

        return sendMessage;
    }

    private SendMessage sendScheduleMessage(String chatId) {
        SendMessage sendMessage = new SendMessage(chatId, "Нажмите ");
        sendMessage.setReplyMarkup(inlineKeyboardMaker.getServiceInlineButton());
        return sendMessage;
    }
}