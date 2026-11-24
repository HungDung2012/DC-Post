"""Verify Java 21 packaging and separation of application and test artifacts."""

from pathlib import Path
import struct
import zipfile


def main():
    root = Path(__file__).resolve().parents[2]
    target = root / "customer-service" / "target"
    for suffix in ("", "-exec"):
        path = target / f"customer-service-0.1.0-SNAPSHOT{suffix}.jar"
        with zipfile.ZipFile(path) as jar:
            names = jar.namelist()
            forbidden = ("junit", "mockito", "spring-test", "application-test", "test-classes")
            if any(fragment in name.lower() for name in names for fragment in forbidden):
                raise RuntimeError(f"Test artifact found in {path.name}")
            class_name = "com/dcpost/customer/CustomerApplication.class"
            if suffix:
                class_name = "BOOT-INF/classes/" + class_name
            elif any(name.startswith("BOOT-INF/") for name in names):
                raise RuntimeError("Main artifact must remain a plain JAR")
            class_bytes = jar.read(class_name)
            if struct.unpack(">H", class_bytes[6:8])[0] != 65:
                raise RuntimeError("Application bytecode must target Java 21")
        print(f"PASS {path.name}: Java 21, expected layout, no test artifacts")


if __name__ == "__main__":
    main()
