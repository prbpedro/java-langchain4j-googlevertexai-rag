package app;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;

public interface Assistant {
    Result<String> chat(@MemoryId int memoryId, @UserMessage String userMessage);
}
