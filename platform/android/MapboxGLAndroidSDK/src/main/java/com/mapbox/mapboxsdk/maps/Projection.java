package com.mapbox.mapboxsdk.maps;

import android.graphics.PointF;
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;

import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.constants.GeometryConstants;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.geometry.ProjectedMeters;
import com.mapbox.mapboxsdk.geometry.VisibleRegion;

import java.util.ArrayList;
import java.util.List;


/**
 * A projection is used to translate between on screen location and geographic coordinates on
 * the surface of the Earth. Screen location is in screen pixels (not display pixels)
 * with respect to the top left corner of the map (and not necessarily of the whole screen).
 */
public class Projection {

  private final NativeMapView nativeMapView;
  private int[] contentPadding;

  Projection(@NonNull NativeMapView nativeMapView) {
    this.nativeMapView = nativeMapView;
    this.contentPadding = new int[] {0, 0, 0, 0};
  }

  void setContentPadding(int[] contentPadding) {
    this.contentPadding = contentPadding;
    nativeMapView.setContentPadding(contentPadding);
  }

  int[] getContentPadding() {
    return contentPadding;
  }

  public void invalidateContentPadding() {
    setContentPadding(contentPadding);
  }

  /**
   * Returns the spherical Mercator projected meters for a LatLng.
   */
  @NonNull
  public ProjectedMeters getProjectedMetersForLatLng(@NonNull LatLng latLng) {
    return nativeMapView.projectedMetersForLatLng(latLng);
  }

  /**
   * Returns the LatLng for a spherical Mercator projected meters.
   */
  @NonNull
  public LatLng getLatLngForProjectedMeters(@NonNull ProjectedMeters projectedMeters) {
    return nativeMapView.latLngForProjectedMeters(projectedMeters);
  }

  /**
   * <p>
   * Returns the distance spanned by one pixel at the specified latitude and current zoom level.
   * </p>
   * The distance between pixels decreases as the latitude approaches the poles.
   * This relationship parallels the relationship between longitudinal coordinates at different latitudes.
   *
   * @param latitude The latitude for which to return the value.
   * @return The distance measured in meters.
   */
  public double getMetersPerPixelAtLatitude(@FloatRange(from = -90, to = 90) double latitude) {
    return nativeMapView.getMetersPerPixelAtLatitude(latitude);
  }

  /**
   * Returns the geographic location that corresponds to a screen location.
   * The screen location is specified in screen pixels (not display pixels) relative to the
   * top left of the map (not the top left of the whole screen).
   *
   * @param point A Point on the screen in screen pixels.
   * @return The LatLng corresponding to the point on the screen, or null if the ray through
   * the given screen point does not intersect the ground plane.
   */
  @NonNull
  public LatLng fromScreenLocation(@NonNull PointF point) {
    return nativeMapView.latLngForPixel(point);
  }

  /**
   * Gets a projection of the viewing frustum for converting between screen coordinates and
   * geo-latitude/longitude coordinates.
   *
   * @return The projection of the viewing frustum in its current state.
   */
  @NonNull
  public VisibleRegion getVisibleRegion() {
    float left = 0;
    float right = nativeMapView.getWidth();
    float top = 0;
    float bottom = nativeMapView.getHeight();


    LatLng center = fromScreenLocation(new PointF(right / 2, bottom / 2));
    Point centerPoint = Point.fromLngLat(center.getLongitude(), center.getLatitude());

    List<LatLng> latLngs = new ArrayList<>();

    LatLng topLeft = fromScreenLocation(new PointF(left, top));
    LatLng topRight = fromScreenLocation(new PointF(right, top));
    LatLng bottomRight = fromScreenLocation(new PointF(right, bottom));
    LatLng bottomLeft = fromScreenLocation(new PointF(left, bottom));

    latLngs.add(topRight);
    latLngs.add(bottomRight);
    latLngs.add(bottomLeft);
    latLngs.add(topLeft);

    double maxEastLonSpan = 0;
    double maxWestLonSpan = 0;

    double east = 0;
    double west = 0;
    double north = GeometryConstants.MIN_LATITUDE;
    double south = GeometryConstants.MAX_LATITUDE;

    for (LatLng latLng : latLngs) {
      Point latLngPoint = Point.fromLngLat(latLng.getLongitude(), latLng.getLatitude());
      double bearing = bearing(centerPoint, latLngPoint);

      if (bearing >= 0) {
        double span = getLongitudeSpan(latLng.getLongitude(), center.getLongitude());
        if (span > maxEastLonSpan) {
          maxEastLonSpan = span;
          east = latLng.getLongitude();
        }
      } else {
        double span = getLongitudeSpan(center.getLongitude(), latLng.getLongitude());
        if (span > maxWestLonSpan) {
          maxWestLonSpan = span;
          west = latLng.getLongitude();
        }
      }

      if (north < latLng.getLatitude()) {
        north = latLng.getLatitude();
      }
      if (south > latLng.getLatitude()) {
        south = latLng.getLatitude();
      }
    }

    return new VisibleRegion(topLeft, topRight, bottomLeft, bottomRight,
      LatLngBounds.from(north, east, south, west));

  }


  /**
   * Takes two {@link Point}s and finds the geographic bearing between them.
   *
   * @param point1 first point used for calculating the bearing
   * @param point2 second point used for calculating the bearing
   * @return bearing in decimal degrees
   * @see <a href="http://turfjs.org/docs/#bearing">Turf Bearing documentation</a>
   */
  static double bearing(@NonNull Point point1, @NonNull Point point2) {

    double lon1 = degreesToRadians(point1.longitude());
    double lon2 = degreesToRadians(point2.longitude());
    double lat1 = degreesToRadians(point1.latitude());
    double lat2 = degreesToRadians(point2.latitude());
    double value1 = Math.sin(lon2 - lon1) * Math.cos(lat2);
    double value2 = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1)
      * Math.cos(lat2) * Math.cos(lon2 - lon1);

    return radiansToDegrees(Math.atan2(value1, value2));
  }

  /**
   * Converts an angle in degrees to radians.
   *
   * @param degrees angle between 0 and 360 degrees
   * @return angle in radians
   */
  static double degreesToRadians(double degrees) {
    double radians = degrees % 360;
    return radians * Math.PI / 180;
  }

  /**
   * Converts an angle in radians to degrees.
   *
   * @param radians angle in radians
   * @return degrees between 0 and 360 degrees
   */
  static double radiansToDegrees(double radians) {
    double degrees = radians % (2 * Math.PI);
    return degrees * 180 / Math.PI;
  }

  /**
   * Get the absolute distance, in degrees, between the west and
   * east boundaries of this LatLngBounds
   *
   * @return Span distance
   */
  static double getLongitudeSpan(double east, double west) {
    double longSpan = Math.abs(east - west);
    if (east > west) {
      return longSpan;
    }

    // shortest span contains antimeridian
    return GeometryConstants.LONGITUDE_SPAN - longSpan;
  }

  /**
   * Returns a screen location that corresponds to a geographical coordinate (LatLng).
   * The screen location is in screen pixels (not display pixels) relative to the top left
   * of the map (not of the whole screen).
   *
   * @param location A LatLng on the map to convert to a screen location.
   * @return A Point representing the screen location in screen pixels.
   */
  @NonNull
  public PointF toScreenLocation(@NonNull LatLng location) {
    return nativeMapView.pixelForLatLng(location);
  }

  float getHeight() {
    return nativeMapView.getHeight();
  }

  float getWidth() {
    return nativeMapView.getWidth();
  }

  /**
   * Calculates a zoom level based on minimum scale and current scale from MapView
   *
   * @param minScale The minimum scale to calculate the zoom level.
   * @return zoom level that fits the MapView.
   */
  public double calculateZoom(float minScale) {
    return nativeMapView.getZoom() + Math.log(minScale) / Math.log(2);
  }
}
