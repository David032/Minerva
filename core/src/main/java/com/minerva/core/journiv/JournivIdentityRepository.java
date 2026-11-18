package com.minerva.core.journiv;

import org.springframework.data.jpa.repository.JpaRepository;

/** Database access to the dedicated Journiv journal identity. */
public interface JournivIdentityRepository extends JpaRepository<JournivIdentity, String> {
}
