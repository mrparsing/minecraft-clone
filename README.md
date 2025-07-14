# Minecraft Clone in Java with LWJGL

Un semplice clone di Minecraft in Java, sviluppato con [LWJGL](https://www.lwjgl.org/) e [JOML](https://github.com/JOML-CI/JOML). Questo progetto implementa un motore voxel 3D con generazione del terreno, chunk dinamici, salvataggio del mondo e interazioni base con i blocchi.

## ✨ Funzionalità principali

- ✅ Rendering 3D a chunk con VBO e VAO
- 🌍 Generazione procedurale del terreno (con biomi, alberi, minerali)
- 💾 Salvataggio e caricamento dei chunk su disco (formato GZIP `.nbt`)
- 🧱 Interazione con blocchi: posizionamento, rimozione e inventario base
- 🔦 Evidenziazione del blocco selezionato con raycasting
- 📦 Texture Atlas (supporto per blocchi multipli)
- 📸 Frustum culling per migliorare le performance
- 🎮 Camera in stile FPS con supporto al mouse e alla tastiera

## 🧱 Requisiti

- Java 17 o superiore
- [LWJGL 3](https://www.lwjgl.org/)
- OpenGL 3.3 o superiore
- JOML (Java OpenGL Math Library)
- Un IDE come IntelliJ IDEA o Eclipse


## 🎮 Controlli

| Tasto | Azione                    |
|-------|---------------------------|
| WASD  | Movimento                 |
| Spazio| Salto                    |
| Mouse | Guarda attorno / Interagisci |
| Click Sinistro | Rompi blocco     |
| Click Destro  | Posiziona blocco  |
| ESC   | Menu principale           |


📦 Sistema di salvataggio

Ogni chunk viene salvato in:

world/<worldName>/<x>_<z>.nbt

Nel formato compresso GZIP. I dati includono dimensioni e tipo di blocco per ogni cella, più supporto futuro per versioni diverse.
