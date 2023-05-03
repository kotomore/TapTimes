package ru.set404.clients.services;

import lombok.AllArgsConstructor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.stereotype.Component;
import ru.set404.clients.dto.telegram.AppointmentMSG;
import ru.set404.clients.models.Appointment;

@Component
@AllArgsConstructor
public class RabbitService {
    private final AmqpTemplate template;
    private TopicExchange telegramExchange;


    public void sendTelegramNotification(Appointment appointment) {
        AppointmentMSG appointmentMSG = new AppointmentMSG();
        appointmentMSG.setAgentId(appointment.getAgentId());
        appointmentMSG.setDate(appointment.getStartTime().toLocalDate().toString());
        appointmentMSG.setStartTime(appointment.getStartTime().toLocalTime().toString());
        appointmentMSG.setEndTime(appointment.getEndTime().toLocalTime().toString());
        appointmentMSG.setClientName(appointment.getClient().getName());
        appointmentMSG.setClientPhone(appointment.getClient().getPhone());
        appointmentMSG.setType(AppointmentMSG.Type.NEW);

        template.convertAndSend(telegramExchange.getName(), "telegram_key.appointment", appointmentMSG);
    }

}