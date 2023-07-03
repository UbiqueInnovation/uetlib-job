CREATE INDEX logging_event_property_mapped_key_index
    ON public.logging_event_property (mapped_key);

CREATE INDEX logging_event_property_mapped_value_index
    ON public.logging_event_property (mapped_value);