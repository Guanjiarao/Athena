package athena.cognition.biz.agenttask.mq;

import athena.athenaframework.mq.producer.MessageQueueProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AgentTaskProducerTest {

    @Mock
    private MessageQueueProducer messageQueueProducer;

    @Test
    void sendDispatchesTaskMessageWithTaskIdAsKeys() {
        AgentTaskProducer producer = new AgentTaskProducer(messageQueueProducer);

        producer.send("task_1", "CLUE_CREATED");

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(messageQueueProducer).send(eq(AgentTaskProducer.TOPIC), eq("task_1"), any(), body.capture());
        assertThat(body.getValue()).isInstanceOf(AgentTaskMessage.class);
        AgentTaskMessage message = (AgentTaskMessage) body.getValue();
        assertThat(message.taskId()).isEqualTo("task_1");
        assertThat(message.triggerType()).isEqualTo("CLUE_CREATED");
    }
}
