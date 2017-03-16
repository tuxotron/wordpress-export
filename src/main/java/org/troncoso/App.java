package org.troncoso;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Arrays;

/*

docker run -it --name mysql -p 3306:3306 -e MYSQL_DATABASE=blog -e MYSQL_USER=blog -e MYSQL_PASSWORD=blog -e MYSQL_ROOT_PASSWORD=blog wangxian/alpine-mysql

docker cp 20170206.sql mysql:/
docker exec -it mysql /bin/sh

mysql blog < 20170206.sql

 */

public class App {

    private static final String DB_SERVER = "localhost";
    private static final int DB_PORT = 3306;
    private static final String DB_NAME = "blog";
    private static final String DB_USER = "blog";
    private static final String DB_PASSWORD = "blog";


    public static void main(String[] args) throws SQLException {

        Connection conn = DriverManager.getConnection(
                String.format("jdbc:mysql://%s:%d/%s", DB_SERVER, DB_PORT, DB_NAME),
                DB_USER, DB_PASSWORD);

        final StringBuilder sql = new StringBuilder("  SELECT\n")
                .append("    users.user_nicename,\n")
                .append("    posts.post_date,\n")
                .append("    posts.post_content,\n")
                .append("    posts.post_title,\n")
                .append("    posts.post_name,\n")
                .append("    cat.categories,\n")
                .append("    tag.tags,\n")
                .append("    concat('/', DATE_FORMAT(posts.post_date,'%Y/%m/%d'), '/', posts.post_name, '/') url,\n")
                .append("    concat(DATE_FORMAT(posts.post_date,'%Y-%m-%d'), '-', posts.post_name, '.md') filename\n")
                .append("  FROM wp_posts posts INNER JOIN wp_users users ON posts.post_author = users.ID\n")
                .append("    INNER JOIN\n")
                .append("    (SELECT\n")
                .append("       rel.object_id           id,\n")
                .append("       group_concat(term.name) categories\n")
                .append("     FROM wp_term_relationships rel\n")
                .append("       INNER JOIN wp_term_taxonomy term_tax ON rel.term_taxonomy_id = term_tax.term_taxonomy_id\n")
                .append("       INNER JOIN wp_terms term ON term_tax.term_id = term.term_id\n")
                .append("     WHERE term_tax.taxonomy = 'category'\n")
                .append("     GROUP BY rel.object_id, term_tax.taxonomy) cat ON posts.ID = cat.ID\n")
                .append("    LEFT JOIN\n")
                .append("    (SELECT\n")
                .append("       rel.object_id           id,\n")
                .append("       group_concat(term.name) tags\n")
                .append("     FROM wp_term_relationships rel\n")
                .append("       INNER JOIN wp_term_taxonomy term_tax ON rel.term_taxonomy_id = term_tax.term_taxonomy_id\n")
                .append("       INNER JOIN wp_terms term ON term_tax.term_id = term.term_id\n")
                .append("     WHERE term_tax.taxonomy = 'post_tag'\n")
                .append("     GROUP BY rel.object_id, term_tax.taxonomy) tag ON posts.ID = tag.ID\n")
                .append("  WHERE posts.post_status = 'publish' AND posts.post_type = 'post'");

        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql.toString());

        while (rs.next()) {

            System.out.println(rs.getString("post_title"));

            createPost(rs);
        }

        rs.close();
        stmt.close();

        conn.close();

    }


    private static void createPost(ResultSet rs) throws SQLException {

        Path path = Paths.get("target/" + rs.getString("filename"));

        try (BufferedWriter writer = Files.newBufferedWriter(path)) {

            writer.write("---");
            writer.newLine();
            writer.write("title: '" + rs.getString("post_title").replace("'", "''") + "'");
            writer.newLine();
            writer.write("author: " + rs.getString("user_nicename"));
            writer.newLine();
            writer.write("date: " + rs.getTimestamp("post_date").toString());
            writer.newLine();
            writer.write("url: " + rs.getString("url"));
            writer.newLine();

            writer.write("categories:");
            writer.newLine();
            String categorias = rs.getString("categories");
            if (categorias != null && !categorias.isEmpty()) {

                Arrays.stream(categorias
                        .split(","))
                        .forEach(category -> {
                            try {
                                writer.write("  - " + category);
                                writer.newLine();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            }

            writer.write("tags:");
            writer.newLine();
            String etiquetas = rs.getString("tags");
            if (etiquetas != null && !etiquetas.isEmpty()) {
                Arrays.stream(etiquetas
                        .split(","))
                        .forEach(tag -> {
                            try {
                                writer.write("  - " + tag.replace("\"", "\\\"").replace(":", "-"));
                                writer.newLine();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            }

            writer.newLine();
            writer.write("---");
            writer.newLine();

            String[] lines = rs.getString("post_content").split(System.getProperty("line.separator"));
            for (String line : lines) {
                writer.write(cleanupVideoLinks(line));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static String cleanupVideoLinks(String line) {

        String cleaned = line;

        if (line.startsWith("https://youtu.be")) {
            cleaned = line.replace("https://youtu.be/", "{{< youtube ");
            cleaned = stripJunk(cleaned);
        } else if (line.startsWith("http://youtu.be")) {
            cleaned = line.replace("http://youtu.be/", "{{< youtube ");
            cleaned = stripJunk(cleaned);
        } else if (line.startsWith("httpv://www.youtube.com")) {
            cleaned = line.replace("httpv://www.youtube.com/watch?v=", "{{< youtube ");
            cleaned = stripJunk(cleaned);
        } else if (line.startsWith("httpv://youtu.be")) {
            cleaned = line.replace("httpv://youtu.be/", "{{< youtube ");
            cleaned = stripJunk(cleaned);
        } else if (line.startsWith("[vimeo]")) {
            cleaned = line.replace("[vimeo]http://vimeo.com/", "{{< vimeo ").replace("[/vimeo]", "");
            cleaned = stripJunk(cleaned);
        }

        return cleaned;

    }

    private static String stripJunk(String line) {
        String cleaned = line;
        if (cleaned.contains("&")) {
            cleaned = cleaned.substring(0, cleaned.indexOf("&") - 1);
        }
        if (cleaned.contains("?")) {
            cleaned = cleaned.substring(0, cleaned.indexOf("?") - 1);
        }
        if (cleaned.contains("#")) {
            cleaned = cleaned.substring(0, cleaned.indexOf("#") - 1);
        }

        if (cleaned.contains("</p>")) {
            cleaned = cleaned.substring(0, cleaned.indexOf("</p>") - 1).replace("\r", "") + " >}}";
        } else {
            cleaned = cleaned.replace("\r", "") + " >}}";
        }

        return cleaned;
    }
}
