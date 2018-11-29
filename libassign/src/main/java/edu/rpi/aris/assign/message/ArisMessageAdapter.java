package edu.rpi.aris.assign.message;

import com.google.gson.*;

import java.lang.reflect.Type;

public class ArisMessageAdapter implements JsonSerializer<Message>, JsonDeserializer<Message> {

    private static final String MESSAGE_TYPE_TAG = "message_type";
    private static final String MESSAGE_BODY_TAG = "message_body";

    @Override
    public Message deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        try {
            JsonObject obj = json.getAsJsonObject();
            String typeName = obj.getAsJsonPrimitive(MESSAGE_TYPE_TAG).getAsString();
            MessageType type = MessageType.valueOf(typeName);
            if (type.msgClass == null)
                throw new JsonParseException("MessageType \"" + type + "\" has not been implemented");
            JsonElement msgBody = obj.get(MESSAGE_BODY_TAG);
            if (msgBody == null)
                throw new JsonParseException("json missing message body");
            return context.deserialize(msgBody, type.msgClass);
        } catch (IllegalStateException e) {
            throw new JsonParseException("json root element is not an object", e);
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("json message type missing or invalid", e);
        }
    }

    @Override
    public JsonElement serialize(Message src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        result.add(MESSAGE_TYPE_TAG, new JsonPrimitive(src.getMessageType().name()));
        result.add(MESSAGE_BODY_TAG, context.serialize(src, src.getClass()));
        return result;
    }

}
