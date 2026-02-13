import fitparse

fitfile = fitparse.FitFile(r"assets\i124672129.fit")

print("=== ALL RECORD MESSAGES WITH DEVELOPER FIELDS ===\n")
for i, record in enumerate(fitfile.get_messages("record")):
    dev_fields = [
        f
        for f in record.fields
        if f.field and hasattr(f.field, "is_developer") and f.field.is_developer
    ]
    if not dev_fields:
        # Also check by name
        dev_fields = [
            f
            for f in record.fields
            if "lactate" in (f.name or "").lower() or "Lactate" in (f.name or "")
        ]
    if dev_fields:
        ts = record.get_value("timestamp")
        print(f"Record #{i} timestamp={ts}")
        for f in record.fields:
            print(f"  {f.name}: {f.value} ({f.units})")
        print()

print("\n=== ALL DEVELOPER FIELD DESCRIPTION MESSAGES ===\n")
for msg in fitfile.get_messages("field_description"):
    print(f"Field Description:")
    for f in msg.fields:
        print(f"  {f.name}: {f.value}")
    print()

print("\n=== ALL DEVELOPER DATA ID MESSAGES ===\n")
for msg in fitfile.get_messages("developer_data_id"):
    print(f"Developer Data ID:")
    for f in msg.fields:
        print(f"  {f.name}: {f.value}")
    print()
