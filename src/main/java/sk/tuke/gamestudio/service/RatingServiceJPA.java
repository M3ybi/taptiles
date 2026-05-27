package sk.tuke.gamestudio.service;

import org.springframework.transaction.annotation.Transactional;
import sk.tuke.gamestudio.entity.Rating;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

@Transactional
public class RatingServiceJPA implements RatingService {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void setRating(Rating rating) throws RatingException {
        List existRating = entityManager.createNamedQuery("Rating.getRating")
                .setParameter("game", rating.getGame()).setParameter("player", rating.getPlayer()).getResultList();
        if (existRating.isEmpty()) {
            entityManager.persist(rating);

        } else {
            int ident = (int) entityManager.createNamedQuery("Rating.getIdent")
                    .setParameter("game", rating.getGame()).setParameter("player", rating.getPlayer()).getSingleResult();
            Rating newRating = entityManager.find(Rating.class, ident);
            newRating.setRating(rating.getRating());
            newRating.setRatedon(rating.getRatedon());
        }
    }

    @Override
    public int getAverageRating(String game) throws RatingException {
        List list = entityManager.createNamedQuery("Rating.getAverageRating").setParameter("game", game).getResultList();
        int sumOfList = 0;
        for (int counter = 0; counter < list.size(); counter++) {
            sumOfList = (int) list.get(counter) + sumOfList;
        }
        return sumOfList / list.size();
    }

    @Override
    public int getRating(String game, String player) throws RatingException {
        return (int) entityManager.createNamedQuery("Rating.getRating")
                .setParameter("game", game).setParameter("player", player).getSingleResult();
    }
}

