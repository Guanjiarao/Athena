package athena.cognition.biz.controller;

import athena.athenaframework.result.Result;
import athena.cognition.biz.domain.CognitionException;
import athena.cognition.biz.domain.CognitionModels.ErrorBody;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Section 12 envelope semantics: semantic code on the outer Result.code,
 * stable errorCode in data.errorCode. Also covers the forged-enum path of
 * TC-22: an unparseable source value (e.g. SQUARE) fails deserialization and
 * maps to COGNITION_INVALID_ARGUMENT.
 */
class CognitionExceptionHandlerTest {

    private final CognitionExceptionHandler handler = new CognitionExceptionHandler();

    @Test
    void domainErrorCarriesSemanticCodeAndStructuredBody() {
        Result<ErrorBody> result = handler.handleDomain(CognitionException.clueInUse("clue_1"));

        assertThat(result.getCode()).isEqualTo(409);
        assertThat(result.getData().errorCode()).isEqualTo(CognitionException.CLUE_IN_USE);
        assertThat(result.getData().objectId()).isEqualTo("clue_1");
    }

    @Test
    void notFoundMapsTo404() {
        Result<ErrorBody> result = handler.handleDomain(CognitionException.notFound());

        assertThat(result.getCode()).isEqualTo(404);
        assertThat(result.getData().errorCode()).isEqualTo(CognitionException.NOT_FOUND);
    }

    @Test
    void versionConflictMapsTo409WithCurrentVersion() {
        Result<ErrorBody> result = handler.handleDomain(CognitionException.versionConflict("digest_9", "3"));

        assertThat(result.getCode()).isEqualTo(409);
        assertThat(result.getData().errorCode()).isEqualTo(CognitionException.VERSION_CONFLICT);
        assertThat(result.getData().currentStatus()).isEqualTo("3");
    }

    @Test
    void unauthenticatedMapsTo401WithoutErrorCode() {
        Result<ErrorBody> result = handler.handleDomain(CognitionException.unauthenticated());

        assertThat(result.getCode()).isEqualTo(401);
        assertThat(result.getData()).isNull();
    }

    @Test
    void tc22ForgedEnumValueFailsParsingAsInvalidArgument() {
        // a forged source=SQUARE never reaches business code: Jackson rejects it
        Result<ErrorBody> result = handler.handleValidation(
                new HttpMessageNotReadableException("Cannot deserialize value of type ClueSource from \"SQUARE\""));

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getData().errorCode()).isEqualTo(CognitionException.INVALID_ARGUMENT);
    }

    @Test
    void uniqueConstraintViolationMapsToStateConflict() {
        Result<ErrorBody> result = handler.handleConflict(new DataIntegrityViolationException("duplicate"));

        assertThat(result.getCode()).isEqualTo(409);
        assertThat(result.getData().errorCode()).isEqualTo(CognitionException.STATE_CONFLICT);
    }
}
