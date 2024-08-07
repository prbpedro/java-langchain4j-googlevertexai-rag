package app;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class QDrantConstants {

    public static final String COLLECTION_NAME = "embedding-collection";
    public static final String HOST = "localhost";
    public static final int PORT = 6334;
    public static final boolean USE_TLS = false;

}
