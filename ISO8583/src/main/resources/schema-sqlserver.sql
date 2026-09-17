
USE ISO8583DB;
GO


IF OBJECT_ID('dbo.CUENTAS', 'U') IS NOT NULL
    DROP TABLE dbo.CUENTAS;
GO

CREATE TABLE dbo.CUENTAS (
    pan VARCHAR(19) NOT NULL PRIMARY KEY,
    balance BIGINT NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT GETDATE()
);
GO


IF OBJECT_ID('dbo.IDEMPOTENCIA', 'U') IS NOT NULL
    DROP TABLE dbo.IDEMPOTENCIA;
GO

CREATE TABLE dbo.IDEMPOTENCIA (
    idempotency_key VARCHAR(100) NOT NULL PRIMARY KEY,
    stan VARCHAR(6) NOT NULL,
    rrn VARCHAR(12) NOT NULL,
    terminal_id VARCHAR(16) NOT NULL,
    response_code VARCHAR(2) NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT GETDATE()
);
GO


IF OBJECT_ID('dbo.sp_ProcesarCompra', 'P') IS NOT NULL
    DROP PROCEDURE dbo.sp_ProcesarCompra;
GO

CREATE PROCEDURE dbo.sp_ProcesarCompra
    @p_pan VARCHAR(19),
    @p_monto BIGINT,
    @o_codigo_respuesta VARCHAR(2) OUTPUT,
    @o_mensaje VARCHAR(150) OUTPUT,
    @o_nuevo_saldo BIGINT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRY
        BEGIN TRANSACTION;


        IF NOT EXISTS (SELECT 1 FROM dbo.CUENTAS WITH (UPDLOCK) WHERE pan = @p_pan)
        BEGIN
            SET @o_codigo_respuesta = '14';
            SET @o_mensaje = 'La tarjeta o cuenta no existe en el sistema.';
            SET @o_nuevo_saldo = 0;
            ROLLBACK TRANSACTION;
            RETURN;
        END

        DECLARE @saldo_actual BIGINT;
        SELECT @saldo_actual = balance FROM dbo.CUENTAS WITH (UPDLOCK) WHERE pan = @p_pan;


        IF (@p_monto > @saldo_actual)
        BEGIN
            SET @o_codigo_respuesta = '51';
            SET @o_mensaje = 'Saldo insuficiente para completar la compra.';
            SET @o_nuevo_saldo = @saldo_actual;
            ROLLBACK TRANSACTION;
            RETURN;
        END


        UPDATE dbo.CUENTAS
        SET balance = balance - @p_monto
        WHERE pan = @p_pan;

        SET @o_nuevo_saldo = @saldo_actual - @p_monto;
        SET @o_codigo_respuesta = '00';
        SET @o_mensaje = 'Compra autorizada y procesada exitosamente.';

        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK TRANSACTION;

        SET @o_codigo_respuesta = '96';
        SET @o_mensaje = ERROR_MESSAGE();
        SET @o_nuevo_saldo = 0;
    END CATCH
END;
GO


IF OBJECT_ID('dbo.sp_ConsultarSaldo', 'P') IS NOT NULL
    DROP PROCEDURE dbo.sp_ConsultarSaldo;
GO

CREATE PROCEDURE dbo.sp_ConsultarSaldo
    @p_pan VARCHAR(19),
    @o_codigo_respuesta VARCHAR(2) OUTPUT,
    @o_saldo BIGINT OUTPUT,
    @o_mensaje VARCHAR(150) OUTPUT
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (SELECT 1 FROM dbo.CUENTAS WHERE pan = @p_pan)
    BEGIN
        SELECT @o_saldo = balance FROM dbo.CUENTAS WHERE pan = @p_pan;
        SET @o_codigo_respuesta = '00';
        SET @o_mensaje = 'Consulta de saldo exitosa.';
    END
    ELSE
    BEGIN
        SET @o_saldo = 0;
        SET @o_codigo_respuesta = '14';
        SET @o_mensaje = 'Tarjeta o cuenta no encontrada.';
    END
END;
GO


IF OBJECT_ID('dbo.sp_GuardarIdempotencia', 'P') IS NOT NULL
    DROP PROCEDURE dbo.sp_GuardarIdempotencia;
GO

CREATE PROCEDURE dbo.sp_GuardarIdempotencia
    @p_key VARCHAR(100),
    @p_stan VARCHAR(6),
    @p_rrn VARCHAR(12),
    @p_terminal_id VARCHAR(16),
    @p_response_code VARCHAR(2)
AS
BEGIN
    SET NOCOUNT ON;
    
    IF NOT EXISTS (SELECT 1 FROM dbo.IDEMPOTENCIA WHERE idempotency_key = @p_key)
    BEGIN
        INSERT INTO dbo.IDEMPOTENCIA (idempotency_key, stan, rrn, terminal_id, response_code, created_at)
        VALUES (@p_key, @p_stan, @p_rrn, @p_terminal_id, @p_response_code, GETDATE());
    END
END;
GO


IF OBJECT_ID('dbo.sp_ConsultarIdempotencia', 'P') IS NOT NULL
    DROP PROCEDURE dbo.sp_ConsultarIdempotencia;
GO

CREATE PROCEDURE dbo.sp_ConsultarIdempotencia
    @p_key VARCHAR(100),
    @o_encontrado BIT OUTPUT,
    @o_response_code VARCHAR(2) OUTPUT
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (SELECT 1 FROM dbo.IDEMPOTENCIA WHERE idempotency_key = @p_key)
    BEGIN
        SELECT @o_response_code = response_code FROM dbo.IDEMPOTENCIA WHERE idempotency_key = @p_key;
        SET @o_encontrado = 1;
    END
    ELSE
    BEGIN
        SET @o_encontrado = 0;
        SET @o_response_code = NULL;
    END
END;
GO


INSERT INTO dbo.CUENTAS (pan, balance) VALUES
('4000000000000001', 5000000),
('4000000000000002', 100000),
('4000000000000003', 2000000);
GO
