package app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.postgresql.util.PGobject;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

public class PersistentChatMemoryStore implements ChatMemoryStore {

    private static final String SELECT = "SELECT chat_messages FROM chats WHERE id = ?";
    private static final String SELECT_IDS = "SELECT id FROM chats";
    private static final String DELETE = "delete from chats where id = ?";
    private static final String UPSERT = """
            INSERT INTO chats (id, chat_messages)
            VALUES (?, ?)
            ON CONFLICT(id)
            DO UPDATE SET
            chat_messages = EXCLUDED.chat_messages;
            """;

    private static final String DB_URL = "jdbc:postgresql://localhost/postgres?user=postgres&password=postgres&ssl=false";

    private final Connection conn;

    public PersistentChatMemoryStore() throws SQLException {
        conn = DriverManager.getConnection(DB_URL);
    }

    public List<Integer> getChatIds() {
        try (Statement statement = conn.createStatement()) {
            try (ResultSet rs = statement.executeQuery(SELECT_IDS)) {
                List<Integer> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
                return ids;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {

        try (PreparedStatement preparedStatement = conn.prepareStatement(SELECT)) {

            preparedStatement.setInt(1, (int) memoryId);

            try (ResultSet rs = preparedStatement.executeQuery()) {
                while (rs.next()) {
                    final String chatMessagesString = rs.getString(1);
                    return ChatMessageDeserializer.messagesFromJson(chatMessagesString);
                }

                return List.of();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try (PreparedStatement preparedStatement = conn.prepareStatement(UPSERT)) {
            final String chatMessages = ChatMessageSerializer.messagesToJson(messages);
            preparedStatement.setInt(1, (int) memoryId);

            final PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            jsonObject.setValue(chatMessages);
            preparedStatement.setObject(2, jsonObject);

            preparedStatement.execute();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        try (PreparedStatement preparedStatement = conn.prepareStatement(DELETE)) {
            preparedStatement.setInt(1, (int) memoryId);
            preparedStatement.execute();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
