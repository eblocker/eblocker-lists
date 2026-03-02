package org.eblocker.lists.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterParser;
import org.eblocker.server.icap.filter.easylist.EasyListLineParser;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Converts an EasyList file to a JSON file (appending ".json" to the input filename)
 */
public class EasyList2Json {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java EasyListStats <easylist file>");
            System.exit(1);
        }
        Path path = Paths.get(args[0]);
        Path dest = Paths.get(path + ".json");
        InputStream inputStream = Files.newInputStream(path);
        OutputStream outputStream = Files.newOutputStream(dest);
        FilterParser parser = new FilterParser(EasyListLineParser::new);
        List<Filter> filters = parser.parse(inputStream);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writeValue(outputStream, filters);
        System.out.println("Wrote " + dest);
    }
}
