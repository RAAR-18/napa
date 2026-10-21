package co.edu.napa.shared.domain.geo;

import co.edu.napa.shared.domain.DomainException;

public record GeoPoint(double latitude, double longitude) {
    public GeoPoint {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new DomainException("VALIDATION_ERROR", "La latitud debe estar entre -90 y 90.");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new DomainException("VALIDATION_ERROR", "La longitud debe estar entre -180 y 180.");
        }
    }
}
