package org.eblocker.lists.parentalcontrol;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.Collections;

public class EmptyListProvider implements BlacklistProvider {
    @Override
    public Blacklist createBlacklist(JsonNode sourceParameters) {
        return new DomainBlacklist(LocalDateTime.now(), Collections.EMPTY_SET);
    }
}
