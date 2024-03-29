package ru.kotomore.telegramservice.messaging;

import org.springframework.amqp.core.Queue;
import telegram.*;
import lombok.AllArgsConstructor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class RabbitMessageSender {
    private final AmqpTemplate template;
    private final Queue telegramQueueFrom;

    public void registerAgentByPhone(String phone) {
        TelegramMessage message = new TelegramMessage();
        message.setAgentId(phone);
        message.setAction(TelegramMessage.Action.REGISTER_BOT);
        template.convertAndSend(telegramQueueFrom.getName(), message);
    }

    public void updateService(AgentServiceMSG service) {
        template.convertAndSend("telegram_update_service", service);
    }

    public void updateSettings(SettingsMSG settingsMSG) {
        template.convertAndSend("telegram_update_settings", settingsMSG);
    }

    public void updateAgent(AgentMSG agentMSG) {
        template.convertAndSend("telegram_update_agent", agentMSG);
    }

    public void updateSchedule(ScheduleMSG scheduleMSG) {
        try {
            template.convertAndSend("telegram_update_schedule", scheduleMSG);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void updateBreak(ScheduleMSG scheduleMSG) {
        try {
            template.convertAndSend("telegram_add_break", scheduleMSG);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void deleteAppointment(String agentId, String appointmentId) {
        AppointmentMSG appointmentMSG = new AppointmentMSG();
        appointmentMSG.setAgentId(agentId);
        appointmentMSG.setAppointmentId(appointmentId);
        template.convertAndSend("telegram_delete_appointment", appointmentMSG);
    }

    public void sendTelegramMessage(String agentId, TelegramMessage.Action action) {
        TelegramMessage message = new TelegramMessage();
        message.setAgentId(agentId);
        message.setAction(action);
        template.convertAndSend(telegramQueueFrom.getName(), message);
    }
}
