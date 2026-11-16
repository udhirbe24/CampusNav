package edu.thapar.campusnav.service;

import edu.thapar.campusnav.model.Pathway;
import edu.thapar.campusnav.model.PathwayClosure;
import edu.thapar.campusnav.model.User;
import edu.thapar.campusnav.repository.PathwayClosureRepository;
import edu.thapar.campusnav.repository.PathwayRepository;
import edu.thapar.campusnav.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Closing and reopening walkways. Every change refreshes the routing graph. */
@Service
public class PathwayService {

    private final PathwayRepository pathways;
    private final PathwayClosureRepository closures;
    private final GraphService graphService;

    public PathwayService(PathwayRepository pathways, PathwayClosureRepository closures, GraphService graphService) {
        this.pathways = pathways;
        this.closures = closures;
        this.graphService = graphService;
    }

    public Pathway get(Long id) {
        return pathways.findById(id).orElseThrow(() -> ApiException.notFound("Pathway #" + id + " not found."));
    }

    @Transactional
    public PathwayClosure close(Long id, String reason, String description, User admin) {
        Pathway pathway = get(id);
        String why = (reason == null || reason.isBlank()) ? "Temporary maintenance" : reason.trim();
        PathwayClosure closure = new PathwayClosure();
        closure.setPathwayId(pathway.getId());
        closure.setReason(why);
        closure.setDescription(description);
        closure.setClosedById(admin.getId());
        pathway.setBlocked(true);
        pathway.setClosureReason(why);
        pathways.save(pathway);
        PathwayClosure saved = closures.save(closure);
        graphService.invalidate(); // new routes must avoid this walkway
        return saved;
    }

    @Transactional
    public Pathway reopen(Long id) {
        Pathway pathway = get(id);
        pathway.setBlocked(false);
        pathway.setClosureReason(null);
        for (PathwayClosure c : closures.findByPathwayIdAndActiveTrue(id)) {
            c.setActive(false);
            c.setActualReopen(Instant.now());
            closures.save(c);
        }
        Pathway saved = pathways.save(pathway);
        graphService.invalidate();
        return saved;
    }
}
