package ru.kotomore.telegramservice.telegram.handlers;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.kotomore.telegramservice.enums.Command;
import ru.kotomore.telegramservice.enums.DefinitionEnum;
import ru.kotomore.telegramservice.enums.EntityEnum;
import ru.kotomore.telegramservice.messaging.RabbitMessageSender;
import ru.kotomore.telegramservice.models.TelegramUser;
import ru.kotomore.telegramservice.models.UserAwaitingResponse;
import ru.kotomore.telegramservice.repositories.TelegramUserRepository;
import ru.kotomore.telegramservice.services.UserAwaitingService;
import ru.kotomore.telegramservice.telegram.keyboards.ReplyKeyboardMaker;
import telegram.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Component
@AllArgsConstructor
public class TelegramMessageHandler {
    private final RabbitMessageSender rabbitMessageSender;
    private final TelegramUserRepository repository;
    private final UserAwaitingService userAwaitingService;

    public BotApiMethod<?> answerMessage(Message message) {
        String chatId = message.getChatId().toString();
        String inputText = message.getText();

        Optional<TelegramUser> user = repository.findByChatId(chatId);
        if (inputText != null && inputText.equals(Command.START.getCommandText())) {
            userAwaitingService.removeFromWaitingList(chatId);
            return getRegMessage(chatId);
        }

        Command inputCommand = Command.fromString(inputText);
        if (inputCommand != null && user.isPresent()) {
            String agentId = user.get().getAgentId();
            switch (inputCommand) {
                case EDIT_SERVICES -> {
                    userAwaitingService.removeFromWaitingList(chatId);
                    rabbitMessageSender.sendTelegramMessage(agentId, TelegramMessage.Action.SERVICE_INFO);
                }
                case PERSONAL_DATA -> {
                    userAwaitingService.removeFromWaitingList(chatId);
                    rabbitMessageSender.sendTelegramMessage(agentId, TelegramMessage.Action.AGENT_INFO);
                }
                case SCHEDULE -> {
                    userAwaitingService.removeFromWaitingList(chatId);
                    rabbitMessageSender.sendTelegramMessage(agentId, TelegramMessage.Action.SCHEDULES);
                }
                case APPOINTMENTS -> {
                    userAwaitingService.removeFromWaitingList(chatId);
                    rabbitMessageSender.sendTelegramMessage(agentId, TelegramMessage.Action.APPOINTMENTS);
                }
                case SETTINGS -> {
                    userAwaitingService.removeFromWaitingList(chatId);
                    rabbitMessageSender.sendTelegramMessage(agentId, TelegramMessage.Action.SETTINGS);
                }
            }
        } else if (user.isEmpty()) {
            return sendNeedAuthMessage(chatId);
        } else if (userAwaitingService.contains(chatId)) {
            return handleAwaitingRequest(message);
        }
        return null;
    }

    private BotApiMethod<?> handleAwaitingRequest(Message message) {
        String chatId = message.getChatId().toString();
        UserAwaitingResponse userAwaitingResponse = userAwaitingService.getWaiter(chatId);
        EntityEnum entityEnum = userAwaitingResponse.entity();
        DefinitionEnum definition = userAwaitingResponse.definition();

        return switch (entityEnum) {
            case SERVICE_ -> handleServiceDefinition(definition, message, chatId);
            case AGENT_ -> handleAgentDefinition(definition, message, chatId);
            case SCHEDULE_ -> handleScheduleDefinition(definition, message, chatId);
            case SETTINGS_ -> handleSettingsDefinition(definition, message, chatId);
            default -> null;
        };
    }

    private BotApiMethod<?> handleServiceDefinition(DefinitionEnum definition, Message message, String chatId) {
        return switch (definition) {
            case NAME -> updateAgentServiceName(message, chatId);
            case DESCRIPTION -> updateAgentServiceDescription(message, chatId);
            case DURATION -> updateAgentServiceDuration(message, chatId);
            case PRICE -> updateAgentServicePrice(message, chatId);
            default -> null;
        };
    }

    private BotApiMethod<?> handleAgentDefinition(DefinitionEnum definition, Message message, String chatId) {
        return switch (definition) {
            case NAME -> updateAgentName(message, chatId);
            case PASSWORD -> updateAgentPassword(message, chatId);
            default -> null;
        };
    }

    private BotApiMethod<?> handleScheduleDefinition(DefinitionEnum definition, Message message, String chatId) {
        if (definition == DefinitionEnum.TIME) {
            return updateAgentSchedule(message, chatId);
        }
        if (definition == DefinitionEnum.BREAK) {
            return updateAgentBreak(message, chatId);
        }
        return null;
    }

    private BotApiMethod<?> handleSettingsDefinition(DefinitionEnum definition, Message message, String chatId) {
        if (definition == DefinitionEnum.URL) {
            SettingsMSG settingsMSG = new SettingsMSG();

            if (message.getText().length() > 20) {
                return new SendMessage(chatId, "Максимальное количество символов - 20");
            }

            if (message.getText().length() < 4) {
                return new SendMessage(chatId, "Минимальное количество символов - 4");
            }

            settingsMSG.setVanityUrl(message.getText());
            return updateUserSettings(chatId, settingsMSG);
        }
        if (definition == DefinitionEnum.BREAK) {
            return updateAgentBreak(message, chatId);
        }
        return null;
    }

    private SendMessage sendNeedAuthMessage(String chatId) {
        String text = "*Необходим вход в приложение*";
        SendMessage sendMessage = new SendMessage(chatId, text);
        sendMessage.enableMarkdown(true);
        ReplyKeyboardMaker maker = new ReplyKeyboardMaker();

        sendMessage.setReplyMarkup(maker.getSingleButtonKeyboard("Войти"));

        return sendMessage;
    }

    private BotApiMethod<?> updateAgentServiceName(Message message, String chatId) {
        AgentServiceMSG service = new AgentServiceMSG();
        if (message.getText().length() > 150) {
            return new SendMessage(chatId, "Максимальное количество символов - 150");
        }
        service.setName(message.getText());
        return updateService(chatId, service);
    }

    private BotApiMethod<?> updateAgentServiceDescription(Message message, String chatId) {
        AgentServiceMSG service = new AgentServiceMSG();
        if (message.getText().length() > 150) {
            return new SendMessage(chatId, "Максимальное количество символов - 150");
        }
        service.setDescription(message.getText());
        return updateService(chatId, service);
    }

    private BotApiMethod<?> updateAgentServiceDuration(Message message, String chatId) {
        AgentServiceMSG service = new AgentServiceMSG();
        try {
            int duration = Integer.parseInt(message.getText());
            service.setDuration(duration);
            return updateService(chatId, service);
        } catch (NumberFormatException exception) {
            return new SendMessage(chatId, "Введите длительность услуги");
        }
    }

    private BotApiMethod<?> updateAgentServicePrice(Message message, String chatId) {
        AgentServiceMSG service = new AgentServiceMSG();
        try {
            double price = Double.parseDouble(message.getText());
            service.setPrice(price);
            return updateService(chatId, service);
        } catch (NumberFormatException exception) {
            return new SendMessage(chatId, "Введите стоимость услуги");
        }
    }

    private BotApiMethod<?> updateService(String chatId, AgentServiceMSG service) {
        Optional<TelegramUser> user = repository.findByChatId(chatId);
        if (user.isPresent()) {
            service.setAgentId(user.get().getAgentId());
            rabbitMessageSender.updateService(service);
            userAwaitingService.removeFromWaitingList(chatId);
        }
        return null;
    }

    private BotApiMethod<?> updateUserSettings(String chatId, SettingsMSG settingsMSG) {
        Optional<TelegramUser> user = repository.findByChatId(chatId);
        if (user.isPresent()) {
            settingsMSG.setAgentId(user.get().getAgentId());
            rabbitMessageSender.updateSettings(settingsMSG);
            userAwaitingService.removeFromWaitingList(chatId);
        }
        return null;
    }

    private BotApiMethod<?> updateAgentName(Message message, String chatId) {
        AgentMSG agentMSG = new AgentMSG();
        agentMSG.setName(message.getText());
        return updateAgent(chatId, agentMSG);
    }

    private BotApiMethod<?> updateAgentPassword(Message message, String chatId) {
        AgentMSG agentMSG = new AgentMSG();
        agentMSG.setPassword(message.getText());
        return updateAgent(chatId, agentMSG);
    }

    private BotApiMethod<?> updateAgent(String chatId, AgentMSG agentMSG) {
        Optional<TelegramUser> user = repository.findByChatId(chatId);
        if (user.isPresent()) {
            if (agentMSG.getName() != null && agentMSG.getName().length() > 20) {
                return new SendMessage(chatId, "Максимальное количество символов - 20");
            }
            if (agentMSG.getPassword() != null && agentMSG.getPassword().length() < 5) {
                return new SendMessage(chatId, "Минимальное количество символов - 5");
            }
            agentMSG.setId(user.get().getAgentId());
            rabbitMessageSender.updateAgent(agentMSG);
            userAwaitingService.removeFromWaitingList(chatId);
        }
        return null;
    }

    private BotApiMethod<?> updateAgentSchedule(Message message, String chatId) {
        ScheduleMSG scheduleMSG = new ScheduleMSG();

        String[] messages = message.getText().split("\n");
        if (messages.length == 4 || messages.length == 3) {
            try {
                scheduleMSG.setAgentId(message.getText());
                scheduleMSG.setDateStart(LocalDate.parse(messages[0]));
                scheduleMSG.setDateEnd(messages.length == 4 ? LocalDate.parse(messages[1]) : scheduleMSG.getDateStart());
                scheduleMSG.setTimeStart(LocalTime.parse(messages[messages.length - 2]));
                scheduleMSG.setTimeEnd(LocalTime.parse(messages[messages.length - 1]));

            } catch (DateTimeParseException ex) {
                return new SendMessage(chatId, "Введите дату и время в указанном формате");
            }

            if (scheduleMSG.getDateEnd().toEpochDay() - scheduleMSG.getDateStart().toEpochDay() > 30) {
                return new SendMessage(chatId, "Разница между датами должна быть менее 30 дней");
            }
            return updateSchedule(chatId, scheduleMSG);
        }
        return new SendMessage(chatId, "Введите дату и время в указанном формате");
    }

    private BotApiMethod<?> updateAgentBreak(Message message, String chatId) {
        ScheduleMSG scheduleMSG = new ScheduleMSG();

        String[] messages = message.getText().split("\n");
        if (messages.length == 4 || messages.length == 3) {
            try {
                scheduleMSG.setAgentId(message.getText());
                scheduleMSG.setDateStart(LocalDate.parse(messages[0]));
                scheduleMSG.setDateEnd(messages.length == 4 ? LocalDate.parse(messages[1]) : scheduleMSG.getDateStart());
                scheduleMSG.setTimeStart(LocalTime.parse(messages[messages.length - 2]));
                scheduleMSG.setTimeEnd(LocalTime.parse(messages[messages.length - 1]));

            } catch (DateTimeParseException ex) {
                return new SendMessage(chatId, "Введите дату и время в указанном формате");
            }

            if (scheduleMSG.getDateEnd().toEpochDay() - scheduleMSG.getDateStart().toEpochDay() > 30) {
                return new SendMessage(chatId, "Разница между датами должна быть менее 30 дней");
            }
            return updateBreak(chatId, scheduleMSG);
        }
        return new SendMessage(chatId, "Введите дату и время в указанном формате");
    }

    private BotApiMethod<?> updateBreak(String chatId, ScheduleMSG schedule) {
        Optional<TelegramUser> user = repository.findByChatId(chatId);
        if (user.isPresent()) {
            schedule.setAgentId(user.get().getAgentId());
            rabbitMessageSender.updateBreak(schedule);
            userAwaitingService.removeFromWaitingList(chatId);
        }
        return null;
    }

    private BotApiMethod<?> updateSchedule(String chatId, ScheduleMSG schedule) {
        Optional<TelegramUser> user = repository.findByChatId(chatId);
        if (user.isPresent()) {
            schedule.setAgentId(user.get().getAgentId());
            rabbitMessageSender.updateSchedule(schedule);
            userAwaitingService.removeFromWaitingList(chatId);
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
        rabbitMessageSender.registerAgentByPhone(phone);
    }

    private SendMessage getRegMessage(String chatId) {
        String text = """
                *TapTimes* – сервис онлайн записи клиентов, предназначен для сферы услуг. Обеспечивает ведение клиентской базы и удобную запись для клиентов. Виджет можно установить на сайт или дать клиенту прямую ссылку.

                *Для входа или регистрации нажмите кнопку ниже.*""";
        SendMessage sendMessage = new SendMessage(chatId, text);
        sendMessage.enableMarkdown(true);
        ReplyKeyboardMaker maker = new ReplyKeyboardMaker();

        sendMessage.setReplyMarkup(maker.getSingleButtonKeyboard("Регистрация"));

        return sendMessage;
    }
}
