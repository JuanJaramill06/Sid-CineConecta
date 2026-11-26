package com.cineconecta.backend.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the profile selected in the "X-Profile-Id" header, already checked in Oracle:
 * it belongs to the account in the JWT and it is active (RNF2, RF12).
 * <pre>
 * &#64;GetMapping
 * public PageResponse&lt;...&gt; search(&#64;CurrentProfile Profile profile, ...) {
 *     int maxAge = profile.getMaxAgeRating();
 * </pre>
 * Missing or non-numeric header: 400. Another account's profile: 403. Inactive: 422.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentProfile {

    String HEADER = "X-Profile-Id";
}
