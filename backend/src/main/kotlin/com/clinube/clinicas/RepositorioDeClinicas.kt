package com.clinube.clinicas

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RepositorioDeClinicas : JpaRepository<Clinica, UUID>
