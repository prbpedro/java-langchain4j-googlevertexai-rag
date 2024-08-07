package app;

import java.util.List;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.service.Result;

public class Utils {

    public static void startConversationWith(PersistentChatMemoryStore store, Assistant assistant) {
        Logger log = LoggerFactory.getLogger(Assistant.class);
        try (Scanner scanner = new Scanner(System.in)) {

            List<Integer> chatIds = store.getChatIds();

            log.info("==================================================");
            log.info("Identificadores de chats: ");
            chatIds.forEach(id -> log.info(id.toString()));

            log.info(
                    "Digite um identificador de chat para continuar um chat existente ou um identificador não existente para iniciar um novo chat: ");

            int chatId = Integer.parseInt(scanner.nextLine());

            while (true) {

                log.info("==================================================");
                log.info("User: ");
                String userQuery = scanner.nextLine();
                log.info("==================================================");

                if ("exit".equalsIgnoreCase(userQuery)) {
                    break;
                }

                Result<String> result = assistant.chat(chatId, userQuery);
                log.info("==================================================");
                log.info("Assistant: " + result.content());
                log.info("Sources: ");
                result.sources().forEach(content -> log.info(content.toString()));
            }
        }
    }

}