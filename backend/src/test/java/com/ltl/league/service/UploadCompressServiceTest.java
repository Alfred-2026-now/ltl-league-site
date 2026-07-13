package com.ltl.league.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UploadCompressServiceTest {

    @Test
    void resolveScanRootLiftsPrizesSubdir() {
        Path prizes = Path.of("/var/www/ltl-league/uploads/prizes");
        assertEquals(Path.of("/var/www/ltl-league/uploads"), UploadCompressService.resolveScanRoot(prizes));
    }

    @Test
    void resolveScanRootKeepsUploadsRoot() {
        Path uploads = Path.of("/var/www/ltl-league/uploads");
        assertEquals(uploads, UploadCompressService.resolveScanRoot(uploads));
    }
}
