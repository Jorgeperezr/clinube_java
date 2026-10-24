"""
Los ajustes de Django para el comparador de contrato: los de `clinube`, con
los archivos subidos en una carpeta temporal en vez de `django-api/media`.
Así el comparador no deja nada dentro del repositorio de Django.
"""
import os

from config.settings import *  # noqa: F401,F403

MEDIA_ROOT = os.environ["MEDIA_DEL_COMPARADOR"]
