package com.example.testapplication2

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

val supabase = createSupabaseClient(
    supabaseUrl = "https://twhaoswxhieeaeqhukau.supabase.co",
    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InR3aGFvc3d4aGllZWFlcWh1a2F1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQ3MjgwMDYsImV4cCI6MjA5MDMwNDAwNn0.xDWj6l-MZLB39go-GTKZPnXpoO4EsHsc5Sul9sCcExw"
) {
    install(Auth)
    install(Postgrest)
}
