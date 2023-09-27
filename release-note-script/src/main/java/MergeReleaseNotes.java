import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MergeReleaseNotes creates the body of the merged release note for ScalarDB. This script takes
 * release note bodies for ScalarDB, ScalarDB Cluster, ScalarDB GraphQL, and ScalarDB SQL as
 * markdown files as its input. And then output the merged release note body to standard output as a
 * markdown.
 *
 * <p>This script is assumed to be executed in a GitHub Actions workflow.
 *
 * <p>Note that it is needed that java 11 to execute this script since it is executed as a
 * Single-File Source-code program.
 */
@SuppressWarnings("DefaultPackage")
public class MergeReleaseNotes {

  private static final String DEBUG = System.getenv("DEBUG");
  private static final String SECTION_SUMMARY = "Summary";

  private final Map<Edition, Map<Category, Map<Repository, List<ReleaseNote>>>> editionMap =
      new EnumMap<>(Edition.class);

  public static void main(String... args) throws Exception {
    if (args.length == 0) {
      System.err.printf(
          "Usage: java --source 11 %s.java scalardb.md cluster.md graphql.md sql.md%n",
          MergeReleaseNotes.class.getSimpleName());
    }

    MergeReleaseNotes mergeReleaseNotes = new MergeReleaseNotes();
    mergeReleaseNotes.createMergedReleaseNote(args);
  }

  public void createMergedReleaseNote(String... args) throws Exception {
    for (String path : args) {
      Edition edition;
      Repository repository;

      switch (path) {
        case "scalardb.md":
          edition = Edition.COMMUNITY;
          repository = Repository.DB;
          break;
        case "cluster.md":
          edition = Edition.ENTERPRISE;
          repository = Repository.CLUSTER;
          break;
        case "graphql.md":
          edition = Edition.ENTERPRISE;
          repository = Repository.GRAPHQL;
          break;
        case "sql.md":
          edition = Edition.ENTERPRISE;
          repository = Repository.SQL;
          break;
        default:
          throw new IllegalArgumentException("Unknown file name: " + path);
      }

      File file = new File(path);
      load(edition, repository, file);
    }
    output();
  }

  void load(Edition edition, Repository repository, File file) throws Exception {
    Category category = null;
    String line;
    Matcher matcher;

    try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
      while ((line = reader.readLine()) != null) {
        if (DEBUG != null) System.err.printf("ReadLine: %s%n", line);
        matcher = Pattern.compile("^## *(\\p{Print}+) *$").matcher(line);
        if (matcher.matches()) {
          if (DEBUG != null) System.err.printf("matched: %s%n", matcher.group(1));
          if (!SECTION_SUMMARY.equalsIgnoreCase(matcher.group(1)))
            category = Category.getByDisplayName(matcher.group(1));
          continue;
        }

        matcher = Pattern.compile("^ *- *(\\p{Print}+)$").matcher(line);
        if (matcher.matches()) {
          if (category == null)
            throw new IllegalStateException(
                "Missing category. Release note text: " + matcher.group(1));
          addReleaseNote(new ReleaseNote(edition, category, repository, matcher.group(1)));
        }
      }
    }
  }

  private void addReleaseNote(ReleaseNote releaseNote) {
    Map<Category, Map<Repository, List<ReleaseNote>>> categoryMap =
        editionMap.computeIfAbsent(releaseNote.edition, k -> new EnumMap<>(Category.class));

    Map<Repository, List<ReleaseNote>> repositoryMap =
        categoryMap.computeIfAbsent(releaseNote.category, k -> new EnumMap<>(Repository.class));

    List<ReleaseNote> releaseNotesList =
        repositoryMap.computeIfAbsent(releaseNote.repository, k -> new ArrayList<>());

    if (releaseNote.edition.equals(Edition.ENTERPRISE)) {
      Matcher matcher =
          Pattern.compile("(.*) +(\\((#[0-9]+ *)+\\))$").matcher(releaseNote.releaseNoteText);
      if (matcher.matches()) {
        if (DEBUG != null)
          System.err.printf(
              "Matched::%s::%s grp1:%s grp2:%s%n",
              releaseNote.category, releaseNote.repository, matcher.group(1), matcher.group(2));
        releaseNote.releaseNoteText = matcher.group(1);
      }
    }

    releaseNotesList.add(releaseNote);
    repositoryMap.put(releaseNote.repository, releaseNotesList);
    categoryMap.put(releaseNote.category, repositoryMap);
    editionMap.put(releaseNote.edition, categoryMap);
  }

  void output() {
    System.out.print("## Summary\n\n");
    Arrays.stream(Edition.values())
        .forEach(
            edition -> {
              outputSections(edition);
              System.out.println();
            });
  }

  private void outputSections(Edition edition) {
    Map<Category, Map<Repository, List<ReleaseNote>>> categoryMap = editionMap.get(edition);
    if (categoryMap == null || categoryMap.isEmpty()) return;

    System.out.printf("## %s edition%n", edition.getEdition());
    Arrays.stream(Category.values())
        .forEach(category -> outputReleaseNotes(category, categoryMap.get(category)));
  }

  private void outputReleaseNotes(
      Category category, Map<Repository, List<ReleaseNote>> repositoryMap) {
    if (repositoryMap == null || repositoryMap.isEmpty()) return;

    System.out.printf("### %s%n", category.getDisplayName());
    Arrays.stream(Repository.values())
        .forEach(
            repository -> {
              List<ReleaseNote> releaseNotes = repositoryMap.get(repository);
              if (releaseNotes != null && !releaseNotes.isEmpty()) {
                if (!repository.equals(Repository.DB))
                  System.out.printf("#### %s%n", repository.getDisplayName());
                for (ReleaseNote rn : releaseNotes) {
                  System.out.printf("- %s%n", rn.releaseNoteText);
                }
              }
            });
  }

  enum Edition {
    COMMUNITY("Community"),
    ENTERPRISE("Enterprise");

    private final String edition;

    Edition(String edition) {
      this.edition = edition;
    }

    public String getEdition() {
      return this.edition;
    }
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

    public static Category getByDisplayName(String displayName) {
      return Arrays.stream(Category.values())
          .filter(v -> v.displayName.equals(displayName))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Invalid displayName: " + displayName));
    }
  }

  enum Repository {
    DB("ScalarDB"),
    CLUSTER("ScalarDB Cluster"),
    GRAPHQL("ScalarDB GraphQL"),
    SQL("ScalarDB SQL");

    private final String displayName;

    Repository(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return this.displayName;
    }
  }

  static class ReleaseNote {
    Edition edition;
    Category category;
    Repository repository;
    String releaseNoteText;

    public ReleaseNote(
        Edition edition, Category category, Repository repository, String releaseNoteText) {
      this.edition = edition;
      this.category = category;
      this.repository = repository;
      this.releaseNoteText = releaseNoteText;
    }
  }
}
