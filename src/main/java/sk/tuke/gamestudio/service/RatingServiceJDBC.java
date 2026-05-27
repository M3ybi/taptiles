package sk.tuke.gamestudio.service;

import sk.tuke.gamestudio.entity.Rating;

import java.sql.*;

public class RatingServiceJDBC implements RatingService {
    public static final String URL = "jdbc:postgresql://localhost/gamestudio";
    public static final String USER = "postgres";
    public static final String PASSWORD = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

    public static final String INSERT_RATING = "INSERT INTO rating (game, player, rating, ratedon) VALUES (?, ?, ?, ?)";

    public static final String SELECT_RATING = "SELECT game, player, rating, ratedon FROM rating WHERE game = ? AND player = ?";

    public static final String SELECT_RATING_AVERAGE = "SELECT game, player, rating, ratedon FROM rating WHERE game = ?";

    public static final String SET_RATING =
            "UPDATE rating SET rating =?, ratedon= ? WHERE game=? AND player=?;";

    @Override
    public void setRating(Rating rating) throws RatingException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
            try (PreparedStatement ps = connection.prepareStatement(SET_RATING)) {
                ps.setInt(1, rating.getRating());
                ps.setDate(2, new Date(rating.getRatedon().getTime()));
                ps.setString(3, rating.getGame());
                ps.setString(4, rating.getPlayer());

                if (ps.executeUpdate() == 0) {
                    try (PreparedStatement ps2 = connection.prepareStatement(INSERT_RATING)) {
                        ps2.setString(1, rating.getGame());
                        ps2.setString(2, rating.getPlayer());
                        ps2.setInt(3, rating.getRating());
                        ps2.setDate(4, new Date(rating.getRatedon().getTime()));

                        ps2.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            throw new RatingException("Error saving rating", e);
        }

    }

    @Override
    public int getAverageRating(String game) throws RatingException {
        int sumOfRatings = 0;
        int totalRatings = 0;

        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
            try (PreparedStatement ps = connection.prepareStatement(SELECT_RATING_AVERAGE)) {
                ps.setString(1, game);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Rating rating = new Rating(
                                rs.getString(1),
                                rs.getString(2),
                                rs.getInt(3),
                                rs.getTimestamp(4)
                        );
                        sumOfRatings += rs.getInt(3);
                        totalRatings++;

                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return sumOfRatings / totalRatings;
    }

    @Override
    public int getRating(String game, String player) throws RatingException {
        Rating rating = null;
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
            try (PreparedStatement ps = connection.prepareStatement(SELECT_RATING)) {
                ps.setString(1, game);
                ps.setString(2, player);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rating = new Rating(
                                rs.getString(1),
                                rs.getString(2),
                                rs.getInt(3),
                                rs.getTimestamp(4)
                        );
                    }
                }
            }
        } catch (SQLException e) {
            throw new RatingException("Error loading rating.", e);
        }

        if (rating != null)
            return rating.getRating();
        else
            return 0;
    }

    public static void main(String[] args) throws Exception {
        RatingService ratingService = new RatingServiceJDBC();
        Rating rating= new Rating("Robo","taptiles",5,new java.util.Date());
        Rating rating2= new Rating("Pato","taptiles",1,new java.util.Date());
        ratingService.setRating(rating);
        ratingService.setRating(rating2);
        System.out.println(ratingService.getAverageRating("taptiles"));
        System.out.println(ratingService.getRating("taptiles","Robo"));
    }
}
