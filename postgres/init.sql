CREATE TABLE IF NOT EXISTS public.chats
(
    id integer NOT NULL,
    chat_messages jsonb,
    CONSTRAINT chats_pkey PRIMARY KEY (id)
);