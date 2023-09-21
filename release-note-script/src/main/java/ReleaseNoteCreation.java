import static java.lang.String.format;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReleaseNoteCreation creates the body of the release note for a
 * repository of Scalar products. The body of the release note is written
 * out as a markdown to standard output.
 * 
 * This script is assumed to be executed in a GitHub Actions workflow.
 * 
 * Note that it is needed that java 11 to execute this script since it is
 * executed as a Single-File Source-code program.
 */
public class ReleaseNoteCreation {

    private final String DEBUG = System.getenv("DEBUG");

    private GitHubContext ghContext;

    Map<Category, List<ReleaseNoteText>> categoryMap = new HashMap<>();
    Map<String, List<ReleaseNoteText>> sameAsItems = new HashMap<>();

    public static void main(String... args) throws Exception {
        if (args.length != 4) {
            System.err.printf(
                "Usage:\n" +
                "    java --source 11 %s.java <owner> <projectTitlePrefix> <version> <repository>\n\n" +
                "Example:\n" +
                "    java --source 11 %s.java scalar-labs ScalarDB 4.0.0 scalardb\n",
                ReleaseNoteCreation.class.getSimpleName(),
                ReleaseNoteCreation.class.getSimpleName());
            System.exit(1);
        }

        String owner = args[0];
        String projectTitlePrefix = args[1];
        String version = args[2];
        String repository = args[3];

        ReleaseNoteCreation main = new ReleaseNoteCreation(owner, projectTitlePrefix, version, repository);
        main.createReleaseNote();
    }

    public ReleaseNoteCreation(String owner, String projectTitlePrefix, String version, String repository) {
        ghContext = new GitHubContext(owner, projectTitlePrefix, version, repository);
    }

    /**
     * This constructor is only for test
     */
    public ReleaseNoteCreation(GitHubContext ghContext) {
        this.ghContext = ghContext;
    }

    void createReleaseNote() throws Exception {
        String projectId = ghContext.getProjectId();
        List<String> prNumbers = ghContext.getPullRequestNumbers(projectId);
              
        for (String prNumber : prNumbers) {
            extractReleaseNoteInfo(prNumber);
        }
        assortSameAsItems();
        outputReleaseNote();
    }

    void extractReleaseNoteInfo(String prNumber) throws Exception {
        if (!ghContext.isPullRequestMerged(prNumber)) return;

        ReleaseNoteText releaseNoteText = new ReleaseNoteText();
        Category category = ghContext.getCategoryFromPullRequest(prNumber);

        BufferedReader br = ghContext.getPullRequestBody(prNumber);

        String line = null;
        while((line = br.readLine()) != null) {
            if (Pattern.matches("^## *[Rr]elease *[Nn]otes? *", line)) {
                releaseNoteText.category = category;
                releaseNoteText.prNumbers.add(prNumber);

                while((line = br.readLine()) != null) {
                    if (Pattern.matches("^## *.*", line)) break; // Reached to the next section header (ended release note section)
                    if (Pattern.matches("^ *-? *N/?A *$", line)) return; // This PR is not user-facing

                    Matcher m = Pattern.compile("^ *-? *(\\p{Print}+)$").matcher(line); // Extract Release note text
                    if (m.matches()) {
                        if (!Pattern.matches("^ *-? *[Ss]ame ?[Aa]s +#?([0-9]+) *$", line)) {
                            String matched = m.group(1);
                            if (DEBUG != null) System.err.printf("matched: %s\n", matched);
                            releaseNoteText.text = m.group(1);
                        }
                    }

                    m = Pattern.compile("^ *-? *[Ss]ame ?[Aa]s +#?([0-9]+) *$").matcher(line);  // It has a related PR
                    if (m.matches()) {
                        String topicPrNumber = m.group(1);
                        if (DEBUG != null)
                            System.err.printf("PR:%s sameAs:%s\n", releaseNoteText.prNumbers.get(0), topicPrNumber);
                        List<ReleaseNoteText> relatedPrs = sameAsItems.get(topicPrNumber);
                        if (relatedPrs == null) relatedPrs = new ArrayList<>();
                        relatedPrs.add(releaseNoteText);
                        sameAsItems.put(topicPrNumber, relatedPrs);
                    }
                }
                categorizeReleaseNoteText(releaseNoteText);
            }
        }
    }

    void categorizeReleaseNoteText(ReleaseNoteText rnText) {
        checkReleaseNoteCategory(rnText);
        Arrays.stream(Category.values()).forEach(category -> {
            if (rnText.category.equals(category)) {
                List<ReleaseNoteText> releaseNoteTexts = categoryMap.get(category);
                if (releaseNoteTexts == null) releaseNoteTexts = new ArrayList<>();
                if (!isContainedInSameAsItems(rnText))
                    releaseNoteTexts.add(rnText);
                categoryMap.put(category, releaseNoteTexts);
            }
        });
    }

    void assortSameAsItems() {
        for (Entry<String, List<ReleaseNoteText>> entry : sameAsItems.entrySet()) {
            String topicPrNumber = entry.getKey();
            List<ReleaseNoteText> releaseNoteTextsInSameAs = entry.getValue();

            Arrays.stream(Category.values()).forEach(category -> {
                List<ReleaseNoteText> releaseNoteTextsInACategory = categoryMap.get(category);
                if (releaseNoteTextsInACategory != null) {
                    releaseNoteTextsInACategory.forEach(rnInfo -> {
                        if (rnInfo.prNumbers.get(0).equals(topicPrNumber)) {
                            releaseNoteTextsInSameAs.forEach(from -> {
                                merge(from, rnInfo);
                            });
                        }
                    });
                }
            });
        }
    }

    void merge(ReleaseNoteText from, ReleaseNoteText to) {
        if (from.text != null && from.text.length() > 0) {
            to.text = to.text + " " + from.text;
        }
        if (DEBUG != null) System.err.printf("merged RN text: %s\n", to.text);
        to.prNumbers.addAll(from.prNumbers);
    }

    void outputReleaseNote() {
        StringBuilder builder = new StringBuilder();
        builder.append("## Summary\n\n");

        Arrays.stream(Category.values()).forEach(category -> {
            List<ReleaseNoteText> releaseNotes = categoryMap.get(category);
            if (releaseNotes != null && !releaseNotes.isEmpty()) {
                builder.append(String.format("## %s\n", category.getDisplayName()));
                builder.append(getFormattedReleaseNotes(category, releaseNotes)).append("\n");
            }
        });
        System.out.println(builder.toString());
    }

    String getFormattedReleaseNotes(Category category, List<ReleaseNoteText> releaseNotes) {
        StringBuilder builder = new StringBuilder();
        releaseNotes.forEach(rnText -> {
            builder.append(String.format("- %s (", rnText.text));
            rnText.prNumbers.forEach(prNum -> {builder.append(String.format("#%s ", prNum));});
            builder.deleteCharAt(builder.length() - 1); // delete the last space character
            builder.append(")\n");
        });
        return builder.toString();
    }

    void checkReleaseNoteCategory(ReleaseNoteText rnText) {
        if (rnText.category == null) {
            rnText.category = Category.MISCELLANEOUS;
        }
    }

    boolean isContainedInSameAsItems(ReleaseNoteText rnText) {
        return sameAsItems.values().stream().anyMatch(items -> {
                return items.contains(rnText);
            });
    }

    enum Category {
        ENHANCEMENT("Enhancements"),
        IMPROVEMENT("Improvements"),
        BUGFIX("Bug fixes"),
        DOCUMENTATION("Documentation"),
        MISCELLANEOUS("Miscellaneous");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public static Category getByName(String name) {
            return Arrays.stream(Category.values())
                    .filter(v -> v.name().toLowerCase().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid name: " + name));
        }

        public static Category getByDisplayName(String displayName) {
            return Arrays.stream(Category.values())
                    .filter(v -> v.displayName.equals(displayName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid displayName: " + displayName));
        }
    }

    class ReleaseNoteText {
        public Category category;
        public String text;
        public List<String> prNumbers = new ArrayList<>();
    }

    class GitHubContext {

        private final String MERGED_STATE = "merged";

        private String owner = "scalar-labs";
        private String projectTitlePrefix = "ScalarDB";
        private String version;
        private String repository;

        public GitHubContext(String owner, String projectTitString, String version, String repository) {
            this.owner = owner;
            this.projectTitlePrefix = projectTitString;
            this.version = version;
            this.repository = repository;
        }
        
        public String getProjectId() throws Exception {
            BufferedReader br = runSubProcessAndGetOutputAsReader(
                format("gh project list --owner %s | awk '/%s/ {print}' | awk '/%s/ {print $1}'",
                this.owner, this.projectTitlePrefix, this.version));

            String line = br.readLine(); // Assuming only one line exists.
            if (line == null) throw new RuntimeException("Couldn't get the projectId");
            return line;
        }

        public List<String> getPullRequestNumbers(String projectId) throws Exception {
            BufferedReader br = runSubProcessAndGetOutputAsReader(
                format("gh project item-list %s --owner %s --limit 200 | awk -F'\\t' '/%s\\t/ {print $3}'",
                projectId, this.owner, this.repository));

            String line = null;
            List<String> prNumbers = new ArrayList<>();
            while((line = br.readLine()) != null) {
                prNumbers.add(line);
            }
            return prNumbers;
        }

        String getPullRequestState(String prNumber) throws Exception {
            BufferedReader br = runSubProcessAndGetOutputAsReader(
                format("gh pr view %s --repo %s/%s --jq \".state\" --json state", prNumber, this.owner, this.repository));

            String line = br.readLine(); // Assuming only one line exists.
            if (line == null) throw new RuntimeException("Couldn't get the projectId");
            return line;
        }

        public boolean isPullRequestMerged(String prNumber) throws Exception {
            String state = getPullRequestState(prNumber);
            return MERGED_STATE.equalsIgnoreCase(state);
        }

        public Category getCategoryFromPullRequest(String prNumber) throws Exception {
            BufferedReader br = runSubProcessAndGetOutputAsReader(
                format("gh pr view %s --repo %s/%s --jq \".labels[].name\" --json labels", prNumber, this.owner, this.repository));

            String line = null;
            while((line = br.readLine()) != null) {
                if (isValidCategory(line)) break;
            }
            if (line == null || line.isEmpty())
                line = Category.MISCELLANEOUS.name().toLowerCase();
            return Category.getByName(line);
        }

        public BufferedReader getPullRequestBody(String prNumber) throws Exception {
            BufferedReader br = runSubProcessAndGetOutputAsReader(
                format("gh pr view %s --repo %s/%s --jq \".body\" --json body",
                prNumber, this.owner, this.repository));
            return br;
        }

        BufferedReader runSubProcessAndGetOutputAsReader(String command) throws Exception {
            if (DEBUG != null) System.err.printf("Executed: %s\n", command);
            Process p = new ProcessBuilder("bash", "-c", command).start();
            p.waitFor();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            return br;
        }

        boolean isValidCategory(String category) {
            return Arrays.stream(Category.values())
                .anyMatch(target -> {return target.name().toLowerCase().equals(category.toLowerCase());});
        }
    }
}
