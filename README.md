# CyMInventory

App Android nativa para organizar las compras de una casa. Esta es una **primera versión local**: permite crear pendientes, convertirlos en compras, adjuntar y consultar tickets, completar fichas y ver el total gastado. Los datos permanecen en el móvil donde se instalaron. La sincronización entre los dos móviles, las cuentas, tareas y notas aún están en desarrollo.

## Abrir y ejecutar

1. Abre esta carpeta en Android Studio: C:\Users\cristian_pato\Desktop\APPS_PERSONALES\APP_CYMINVENTORY\cyminventory.
2. Deja que Gradle termine de sincronizar. El proyecto utiliza el SDK Android 37.
3. Para un móvil: activa *Opciones de desarrollador* y *Depuración USB*, conéctalo y acepta la huella RSA cuando aparezca.
4. Selecciona el móvil en la barra superior de Android Studio y pulsa **Run ▶**. También puedes instalar el APK de app/build/outputs/apk/debug/app-debug.apk en el móvil.
5. Para el emulador LifeCym_Test: abre **Device Manager**, edítalo o crea uno nuevo y descarga la imagen Android 34 / Google APIs / x86_64, que aún falta en el SDK. Luego selecciónalo y pulsa **Run ▶**.

Para compilar desde terminal en Windows, ejecuta gradlew.bat :app:assembleDebug; asegúrate de que Android Studio y el SDK están instalados.

## Estado

- Pendientes: añadir, buscar, filtrar por habitación, editar y registrar compra.
- Compras: nombre, precio, fecha, ticket, habitación, categoría, tienda, ubicación, uso y descripción; edición posterior.
- Tickets: selección de foto o PDF, copia al almacenamiento privado de la app y apertura con otra aplicación del móvil.
- Gastos: total de artículos comprados.
- Compartido: pantalla reservada para tareas y notas; aún sin sincronización.

Consulta docs/PRODUCT.md y docs/DESIGN.md para el alcance y las decisiones visuales. docs/preview.html es el prototipo de diseño aprobado.
