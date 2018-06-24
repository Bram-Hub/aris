package edu.rpi.aris.net.message;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

public class ZDTGsonAdapter implements JsonSerializer<ZonedDateTime>, JsonDeserializer<ZonedDateTime> {

    @Override
    public ZonedDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        String zdtString = null;
        try {
            zdtString = json.getAsString();
            return ZonedDateTime.parse(zdtString);
        } catch (DateTimeParseException e) {
            throw new JsonParseException("Invalid ZonedDateTime String: " + zdtString);
        }
    }

    @Override
    public JsonElement serialize(ZonedDateTime src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
    }
}
