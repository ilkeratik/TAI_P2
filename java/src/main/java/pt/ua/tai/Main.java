package pt.ua.tai;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import pt.ua.tai.meta.Meta;

import java.util.Map;
import java.util.concurrent.Callable;

import static pt.ua.tai.database.DatabaseReader.readFileAndCreateDbMap;
import static pt.ua.tai.database.DatabaseReader.readTxtFileToString;

public class Main implements Callable<Integer> {
    @Option(names = {"-fm", "--file-meta"}, description = "File path for meta", defaultValue = "sequences/meta.txt", required = true)
    private String fileNameMeta;
    @Option(names = {"-fd", "--file-db"}, description = "File path for db/sequences", defaultValue = "sequences/db.txt", required = true)
    private String fileNameDb;
    @Option(names = {"-v", "--verbose"}, description = "Verbose output", defaultValue = "false")
    private boolean verbose;
    @Option(names = {"-a", "--alpha"}, description = "Smoothing parameter alpha", defaultValue = "1")
    private Float alpha;
    @Option(names = {"-k", "--contextWidth"}, description = "Context width", defaultValue = "13")
    private Integer k;
    @Option(names = {"-t", "--top"}, description = "Top t results", defaultValue = "20")
    private Integer t;

    public static void main(String[] args) {
        System.setProperty("java.util.logging.SimpleFormatter.format", "Time: %1$tT.%1$tL -> %4$s %5$s%6$s%n");
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            Map<String, String> dbMap = readFileAndCreateDbMap(fileNameDb);
            String metaContent = readTxtFileToString(fileNameMeta);
            Meta meta = new Meta(metaContent, k);
            Map<String, Double> best = meta.getBestSequences(dbMap, alpha, t);

            best.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .forEach(e -> System.out.println(e.getValue() + "\t" + e.getKey()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return 1;
    }
}